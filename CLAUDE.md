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
├── evm-chain-adapter/           ← Forge's domain (real Ethereum adapter)
│   └── CLAUDE.md
├── funky-wallet-e2e/            ← Scout's domain (Playwright e2e tests)
│   └── CLAUDE.md
├── scripts/                     ← start/stop scripts
│   ├── start-dev.sh             # bring up local profile
│   ├── stop-dev.sh
│   ├── start-e2e.sh             # bring up full e2e stack
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
| Scout | E2E tests | `funky-wallet-e2e/` | — |

## System architecture

### Local / dev profile (Hoodi testnet)
```
Browser → Auth0 Universal Login
              ↓ JWT (sub claim = userId)
         funky-wallet-ui (:3000)
              ↓ /api proxy  (Bearer token on every request)
         wallet-api-service (:8080)
              ↓ JwtAuthenticationFilter extracts sub → SecurityContext
              ↓ signing calls               ↓ chain calls
  mock-signing-coordinator (:9000)   evm-chain-adapter (:9090)
       ↓ MPC rounds                         ↓ JSON-RPC
  mock-mpc-node-1/2/3 (:9011-9013)   Hoodi testnet (remote)
```

### E2E profile (local Geth)
```
funky-wallet-ui (:3000, VITE_AUTH_DISABLED=true)
         ↓ /api proxy (no auth header — E2E_USER_ID env var used instead)
wallet-api-service (:8080, E2E_USER_ID=e2e|test-user)
         ↓                          ↓
mock-signing-coordinator (:9000)  evm-chain-adapter (:9090)
                                         ↓
                                  Geth dev node (:8545, chainId 1337)
```

## Rules

- Each sub-agent owns its directory exclusively — never cross boundaries
- Mnemonic must never appear in logs, DB, or persistent state anywhere in the stack
- API contract between Pixel and Forge is defined in `wallet-api-service/CLAUDE.md`
- JWT validation is NOT in the app — Istio owns it in prod; app only extracts `sub` claim
- Per-user scoping: all account and transaction queries are filtered by userId from JWT sub
- Block watcher runs every 15s, detects incoming transactions via fromAddress/toAddress matching

## Scripts

```bash
bash scripts/start-dev.sh   # local profile: Postgres + evm-chain-adapter (Hoodi) + signing + API + UI
bash scripts/stop-dev.sh

bash scripts/start-e2e.sh   # e2e stack: Geth + Postgres + all services in Docker
bash scripts/stop-e2e.sh
cd funky-wallet-e2e && npm test   # run Playwright tests
```
