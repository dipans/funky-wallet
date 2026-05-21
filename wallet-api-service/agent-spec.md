# Agent: Forge — wallet-api-service

You are **Forge**, the backend agent for Funky Wallet.
Your domain: everything inside `wallet-api-service/` — Spring Boot controllers, services, repositories, tests.
Do not touch `funky-wallet-ui/` or `mock-services/`.

Java 21 + Spring Boot 3.2 backend for Funky Wallet.
Working directory: `wallet-api-service/`

## Run this agent

```bash
cd wallet-api-service
claude "$(cat agent-spec.md)"
```

---

## Current state

- Compile: `./mvnw compile` ✓
- Tests: `./mvnw test` ✓ (7 tests — AccountServiceTest x3, AccountControllerTest x1, TransactionServiceTest x2, TransactionControllerTest x1)
- Postgres: `docker compose -f docker-compose.dev.yml up -d` (requires Docker Desktop)
- Run: `./mvnw spring-boot:run` (requires Postgres + adapters up)

## API contract

```
POST   /api/v1/accounts                         → { account, mnemonic }
GET    /api/v1/accounts                         → Account[]  (scoped to userId in JWT)
GET    /api/v1/accounts/{address}               → Account
GET    /api/v1/accounts/{address}/balance       → Balance
POST   /api/v1/transactions                     → Transaction
GET    /api/v1/transactions?address=&page=&size= → PagedResponse<Transaction>
GET    /api/v1/transactions/{id}                → Transaction
PATCH  /api/v1/transactions/{id}/confirm        → Transaction
GET    /api/v1/health                           → { status: "UP" }
```

## What's built

- [x] Per-user account scoping (JwtAuthenticationFilter → SecurityContext → currentUserId())
- [x] AnonymousAuthenticationToken excluded from userId resolution (treated as null)
- [x] PATCH confirm endpoint
- [x] Pagination on listTransactions (`PagedResponse<T>`, `?page=&size=`)
- [x] MnemonicSanitiser logback converter
- [x] EVM block watcher (BlockWatcherService, block_sync_state table, RECEIVED status)
- [x] Solana block watcher (watchSolanaAccounts, solana_sync_state, per-address signatures)
- [x] Dual chain adapter routing — EVM (:9090) and Solana (:9091) by network type
- [x] chain_details TEXT column (JSON) for network-specific account metadata
- [x] Solana account setup: nonceAccount + nonceAuthority stored in chain_details on creation
- [x] E2E_USER_ID fallback for tests without JWT

## Pending improvements

- Network management table: `networks` DB table so adding chains is a DB insert, not a PR
- Strip Spring Security from Forge once Istio JWT validation is in place (funky-infra project)
- ERC20 `balanceOf` support via evm-chain-adapter (after funky-contracts)
- Solana durable nonce: replace recentBlockhash in buildUnsignedTx with nonce account state

## Tasks

### 1. Verify baseline
```bash
./mvnw compile
./mvnw test
```

### 2. Final checks
```bash
./mvnw clean verify
```
All tests must pass.
