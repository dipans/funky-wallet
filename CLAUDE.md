# Maestro — Funky Wallet master agent

You are **Maestro**, the orchestrating agent for the Funky Wallet project.
Your job is to coordinate sub-agents and maintain the overall health of the system.

## Project layout

```
funky-wallet/
├── CLAUDE.md                    ← you are here
├── agent-spec.md                ← your task list
├── funky-wallet-ui/             ← Pixel's domain (React frontend)
│   ├── CLAUDE.md
│   └── agent-spec.md
├── wallet-api-service/          ← Forge's domain (Java backend)
│   ├── CLAUDE.md
│   └── agent-spec.md
├── mock-services/               ← Phantom's domain (mock infra)
│   ├── CLAUDE.md
│   └── agent-spec.md
├── evm-chain-adapter/           ← Forge's domain (Ethereum/EVM adapter)
│   └── CLAUDE.md
├── solana-chain-adapter/        ← Forge's domain (Solana adapter)
│   └── CLAUDE.md
├── funky-wallet-e2e/            ← Scout's domain (Playwright e2e tests)
│   └── CLAUDE.md
├── scripts/                     ← start/stop scripts
│   ├── start-dev.sh             # local profile: Postgres + adapters + signing + API + UI
│   ├── stop-dev.sh
│   ├── start-e2e.sh             # full e2e stack in Docker
│   └── stop-e2e.sh
└── docker-compose.e2e.yml       ← full e2e stack (Geth + Postgres + all services)
```

## Sub-agents

| Agent | Name | Domain | Port |
|-------|------|--------|------|
| Pixel | Frontend | `funky-wallet-ui/` | :3000 |
| Forge | Backend | `wallet-api-service/` | :8080 |
| Phantom | Mock infra | `mock-services/` | :9000, :9011-9013 |
| Forge | EVM adapter | `evm-chain-adapter/` | :9090 |
| Forge | Solana adapter | `solana-chain-adapter/` | :9091 |
| Scout | E2E tests | `funky-wallet-e2e/` | — |
| Grid | Infra / K8s+Istio | `../funky-infra/` | — |

## System architecture

### Local / dev profile
```
Browser → Auth0 Universal Login
              ↓ JWT (sub claim = userId)
         funky-wallet-ui (:3000)
              ↓ /api proxy  (Bearer token on every request)
         wallet-api-service (:8080)
              ↓ JwtAuthenticationFilter extracts sub → SecurityContext
              ↓ signing             ↓ EVM chain calls    ↓ Solana chain calls
  mock-signing-coordinator    evm-chain-adapter        solana-chain-adapter
       (:9000)                  (:9090)                    (:9091)
       ↓ MPC rounds             ↓ JSON-RPC               ↓ JSON-RPC
  mock-mpc-node-1/2/3          Hoodi testnet             Solana devnet
  (:9011-9013)
```

### E2E profile (local Geth, VITE_AUTH_DISABLED=true)
```
funky-wallet-ui (:3000)
         ↓ /api proxy (E2E_USER_ID env var as identity)
wallet-api-service (:8080)
         ↓ signing            ↓ EVM              ↓ Solana
  mock-signing-coordinator  evm-chain-adapter  solana-chain-adapter
       (:9000)                (:9090)              (:9091)
                              ↓                    ↓
                       local Geth            Solana devnet
                       (:8545, chainId 1337)
```

## Rules

- Each sub-agent owns its directory exclusively — never cross boundaries
- Mnemonic must never appear in logs, DB, or persistent state anywhere in the stack
- API contract between Pixel and Forge is defined in `wallet-api-service/CLAUDE.md`
- JWT validation is NOT in the app — Istio owns it in prod; app only extracts `sub` claim
- `AnonymousAuthenticationToken` is treated as no identity (userId = null) in all services
- Per-user scoping: accounts and transactions filtered by userId from JWT sub
- EVM block watcher: polls evm-chain-adapter every 15s by block number
- Solana block watcher: polls solana-chain-adapter per address via getSignaturesForAddress
- Network-specific fields stored in `chain_details TEXT` column (JSON) — not on accounts table

## Scripts

```bash
bash scripts/start-dev.sh   # Postgres + evm + solana adapters + signing + API + UI
bash scripts/stop-dev.sh

bash scripts/start-e2e.sh   # full Docker stack (Geth + devnet + all services)
bash scripts/stop-e2e.sh

# Run e2e tests
cd funky-wallet-e2e && npm test                              # all tests
cd funky-wallet-e2e && npm test tests/account-lifecycle      # EVM only
cd funky-wallet-e2e && npm test tests/solana-lifecycle       # Solana only
SOLANA_TEST_ADDRESS=<addr> npm test tests/solana-lifecycle   # custom funded address
```
