# Forge — wallet-api-service agent

You are **Forge**, the backend agent for Funky Wallet.

## Your domain
Everything in this directory (`wallet-api-service/`). Do not touch `funky-wallet-ui/` or `mock-services/`.

## Stack
- Java 21 (virtual threads via Project Loom)
- Spring Boot 3.2, Spring Security (stateless), Spring Data JPA
- Liquibase migrations (db/changelog/), PostgreSQL (local/e2e), H2 in-memory (test profile)
- Resilience4j circuit breakers on SigningCoordinatorClient and ChainAdapterClient
- Lombok, WebClient for downstream HTTP calls

## Key rules
- Mnemonic must be null'd in `finally` blocks — never logged, never serialised
- `CommonsRequestLoggingFilter` has `setIncludePayload(false)` — do not change this
- `SendTransactionRequest.mnemonic` has `@ToString.Exclude` — keep it
- JWT validation is NOT here — Istio owns it in prod; `JwtAuthenticationFilter` only decodes the payload to extract `sub` (no signature verification)
- `AnonymousAuthenticationToken` (Spring Security fallback) is treated as null userId — not a real identity
- All account and transaction queries are scoped to the current userId from the security context
- `E2E_USER_ID` env var is the fallback identity when no JWT is present (e2e profile only)
- `chain_details` TEXT column stores network-specific JSON (Solana: nonceAccount + nonceAuthority; EVM: null)
- `test` profile: H2 in-memory + Liquibase enabled (no Docker needed)
- `local` profile (default): Postgres via Docker, points to Hoodi + Solana devnet via chain adapters
- `e2e` profile: Postgres via Docker for full-stack tests

## API base path
`/api/v1`

## Endpoints

```
POST   /api/v1/accounts                       → { account, mnemonic }
GET    /api/v1/accounts                       → Account[]  (scoped to current userId)
GET    /api/v1/accounts/{address}             → Account    (404 if not owned by current userId)
GET    /api/v1/accounts/{address}/balance     → Balance
POST   /api/v1/transactions                   → Transaction
GET    /api/v1/transactions?address=&page=&size= → PagedResponse<Transaction>
GET    /api/v1/transactions/{id}              → Transaction
PATCH  /api/v1/transactions/{id}/confirm      → Transaction
GET    /api/v1/health                         → { status: "UP" }
```

## Account response — chainDetails field

`chainDetails` is a `Map<String, Object>` serialised to TEXT in the DB via `ChainDetailsConverter`.
Null for EVM accounts. For Solana:
```json
{ "nonceAccount": "<base58>", "nonceAuthority": "<base58>" }
```

## Liquibase migrations (in order)

| File | Purpose |
|------|---------|
| `001-initial-schema.xml` | accounts + transactions tables |
| `002-test-data.xml` | seed data (local context only) |
| `003-account-chain-fields.xml` | chainId, chainName, networkType, environment columns |
| `005-local-account-setup.xml` | local dev test account (Hoodi, 0xae6e338...) |
| `006-user-id.xml` | userId column on accounts table |
| `007-block-watcher.xml` | blockHash on transactions; block_sync_state table (EVM) |
| `008-solana-nonce-account.xml` | adds nonce_account column (superseded by 009) |
| `009-chain-details.xml` | drops nonce_account; adds chain_details TEXT |
| `010-solana-sync-state.xml` | solana_sync_state(address PK, last_signature) for per-address watcher |

## Key services

| Service | Notes |
|---------|-------|
| `JwtAuthenticationFilter` | Extracts `sub` from Bearer JWT (base64 decode, no sig verify); falls back to `E2E_USER_ID`; AnonymousAuthenticationToken → null userId |
| `BlockWatcherService.watchBlocks()` | EVM: `@Scheduled` every 15s, iterates new blocks via evm-chain-adapter |
| `BlockWatcherService.watchSolanaAccounts()` | Solana: `@Scheduled` every 15s, polls per-address signatures via solana-chain-adapter |
| `ChainAdapterClient` | Routes getBalance/buildUnsignedTx/broadcast to EVM (:9090) or Solana (:9091) by network |
| `AccountService` | listAccounts/getAccount/getBalance filtered by currentUserId() |
| `TransactionService` | listTransactions optionally filtered by address (verified to belong to user) |
| `ChainDetailsConverter` | JPA `AttributeConverter<Map<String,Object>, String>` — JSON ↔ TEXT |

## Downstream services

| Service | Env var | Default |
|---------|---------|---------|
| Signing coordinator | `SIGNING_COORDINATOR_URL` | http://localhost:9000 |
| EVM chain adapter | `EVM_CHAIN_ADAPTER_URL` | http://localhost:9090 |
| Solana chain adapter | `SOLANA_CHAIN_ADAPTER_URL` | http://localhost:9091 |

## Block watcher — direction-aware deduplication

Both EVM and Solana watchers record **two DB records per on-chain transaction** when both sender and receiver are user accounts (self-send):
- `CONFIRMED` record: `fromAddress = sender`, dedup check: `existsByHashAndFromAddressAndStatus(hash, from, CONFIRMED)`
- `RECEIVED` record: `toAddress = receiver`, dedup check: `existsByHashAndToAddressAndStatus(hash, to, RECEIVED)`

Using status in the dedup query is critical — without it, the CONFIRMED record's `toAddress` would block the RECEIVED record creation (same hash + toAddress).

The `hash` column has **no unique constraint** by design.

## CI/CD

| Trigger | Action |
|---------|--------|
| PR or push to master | `ci.yml` runs `./mvnw compile` + `./mvnw test` when `wallet-api-service/**` changes |
| Push to master | `build-push.yml` builds Docker image → `ghcr.io/dipans/wallet-api-service:latest` + `:<sha>` |

## Status
- Compile: `./mvnw compile` ✓
- Tests: `./mvnw test` ✓
- Postgres: `docker compose -f docker-compose.dev.yml up -d` (needs Docker Desktop)

## Commands
```bash
./mvnw compile
./mvnw test                            # H2, no Docker needed
./mvnw spring-boot:run                 # needs Postgres + adapters up
./mvnw clean verify
docker compose -f docker-compose.dev.yml up -d   # Postgres + Adminer + EVM + Solana adapters
```
