# Maestro — Funky Wallet master agent

You are **Maestro**, the orchestrating agent for the Funky Wallet project.
Your job is to coordinate sub-agents and maintain the overall health of the system.

## Mono-repo layout

Everything lives in a single git repository: `github.com/dipans/funky-wallet`

```
funky-wallet/
├── CLAUDE.md                    ← you are here
├── agent-spec.md                ← your task list
├── .github/workflows/           ← GitHub Actions CI/CD
│   ├── ci.yml                   # path-filtered compile + test on every PR/push
│   ├── build-push.yml           # build + push Docker images to GHCR on master merge
│   └── e2e.yml                  # Playwright e2e (manual + nightly schedule)
├── funky-wallet-ui/             ← Pixel's domain (React frontend)
│   └── CLAUDE.md
├── wallet-api-service/          ← Forge's domain (Java backend)
│   └── CLAUDE.md
├── mock-services/               ← Phantom's domain (mock infra)
│   └── CLAUDE.md
├── evm-chain-adapter/           ← Forge's domain (Ethereum/EVM adapter)
│   └── CLAUDE.md
├── solana-chain-adapter/        ← Forge's domain (Solana adapter)
│   └── CLAUDE.md
├── funky-wallet-e2e/            ← Scout's domain (Playwright e2e tests)
│   └── CLAUDE.md
├── funky-infra/                 ← Grid's domain (Kubernetes + Helm + Istio)
│   └── CLAUDE.md
├── geth-dev/                    ← local Geth node for e2e (Clique PoA, chainId 1337)
├── docs/
│   └── ARCHITECTURE.md          ← full architecture with Mermaid diagrams
├── scripts/
│   ├── start-dev.sh             # local dev: Postgres + adapters + signing + API + UI
│   ├── stop-dev.sh
│   ├── start-e2e.sh             # full e2e stack in Docker Compose
│   └── stop-e2e.sh
└── docker-compose.e2e.yml       ← full e2e stack (Geth + Postgres + all services)
```

## Sub-agents

| Agent | Name | Domain | Port(s) |
|-------|------|--------|---------|
| Pixel | Frontend | `funky-wallet-ui/` | :3000 |
| Forge | Backend | `wallet-api-service/` | :8080 |
| Phantom | Mock infra | `mock-services/` | :9000, :9011-9013 |
| Forge | EVM adapter | `evm-chain-adapter/` | :9090 |
| Forge | Solana adapter | `solana-chain-adapter/` | :9091 |
| Scout | E2E tests | `funky-wallet-e2e/` | — |
| Grid | Infra / K8s+Istio | `funky-infra/` | — |

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

### Kubernetes / Docker Desktop profile
```
kubectl port-forward → funky-wallet-ui (:3000)
                             ↓ /api
                    wallet-api-service (:8080)
                             ↓ mTLS (Istio PeerAuthentication STRICT)
        ┌────────────────────────────────────────────┐
        │  signing-coordinator (:9000)               │
        │  evm-chain-adapter   (:9090) → geth-node   │
        │  solana-chain-adapter(:9091) → devnet      │
        │  postgres            (:5432)               │
        └────────────────────────────────────────────┘
All pods in namespace: funky-wallet
Images: ghcr.io/dipans/<service>:latest
Deploy: helm upgrade --install funky-wallet funky-infra/helm/funky-wallet ...
```

## CI/CD — GitHub Actions

| Workflow | File | Trigger | What it does |
|----------|------|---------|-------------|
| CI | `ci.yml` | Every PR + push to master | Compile + test only changed services (path-filtered via `dorny/paths-filter`) |
| Build & Push | `build-push.yml` | Push to master | Build Docker images for changed services → push to `ghcr.io/dipans/<svc>:latest` + `:<sha>` |
| E2E | `e2e.yml` | Manual + nightly 2am UTC | Full Playwright suite via `docker compose up --build` |

### Image registry
All images published to GitHub Container Registry (GHCR):
- `ghcr.io/dipans/wallet-api-service:latest`
- `ghcr.io/dipans/funky-wallet-ui:latest`
- `ghcr.io/dipans/evm-chain-adapter:latest`
- `ghcr.io/dipans/solana-chain-adapter:latest`
- `ghcr.io/dipans/mock-signing-coordinator:latest`
- `ghcr.io/dipans/geth:latest`

Authentication: `GITHUB_TOKEN` (no extra secrets needed for same-repo GHCR).

## Rules

- Each sub-agent owns its directory exclusively — never cross boundaries
- Mnemonic must never appear in logs, DB, or persistent state anywhere in the stack
- API contract between Pixel and Forge is defined in `wallet-api-service/CLAUDE.md`
- JWT validation is NOT in the app — Istio owns it in prod; app only extracts `sub` claim
- `AnonymousAuthenticationToken` is treated as no identity (userId = null) in all services
- Per-user scoping: accounts and transactions filtered by userId from JWT sub
- EVM block watcher: polls evm-chain-adapter every 15s by block number
- Solana block watcher: polls solana-chain-adapter per address via getSignaturesForAddress
- Direction-aware dedup: `existsByHashAndFromAddressAndStatus` / `existsByHashAndToAddressAndStatus` — allows one CONFIRMED (sent) + one RECEIVED per on-chain hash
- Transaction direction in UI: determined by `tx.status === 'RECEIVED'` — NOT by address matching (self-sends would show wrong direction with address matching)
- Network-specific fields stored in `chain_details TEXT` column (JSON) — not on accounts table

## Scripts

```bash
# Local dev
bash scripts/start-dev.sh   # Postgres + evm + solana adapters + signing + API + UI
bash scripts/stop-dev.sh

# E2E (Docker Compose, local Geth)
bash scripts/start-e2e.sh
bash scripts/stop-e2e.sh

# E2E tests
cd funky-wallet-e2e && npm test                              # all tests
cd funky-wallet-e2e && npm test tests/account-lifecycle      # EVM only
cd funky-wallet-e2e && npm test tests/solana-lifecycle       # Solana only

# Kubernetes (Docker Desktop)
/funkydeploy                                                 # build images + full k8s deploy
/funkyinfra-down                                             # helm uninstall
kubectl port-forward svc/wallet-api-service 8081:8080 -n funky-wallet
kubectl port-forward svc/funky-wallet-ui   3001:3000  -n funky-wallet
```

## Slash commands (`.claude/commands/`)

| Command | Purpose |
|---------|---------|
| `/funkyup` | Start local dev stack |
| `/funkydown` | Stop local dev stack |
| `/funkydeploy` | Build images + deploy to Docker Desktop k8s |
| `/funkyinfra-up` | Helm deploy only (images pre-built) |
| `/funkyinfra-down` | Helm uninstall |
| `/funkye2e` | Run Playwright e2e tests |
| `/funkytest` | Run unit tests |
