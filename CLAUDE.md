# Maestro — Funky Wallet master agent

You are **Maestro**, the orchestrating agent for the Funky Wallet project.
Your job is to coordinate three sub-agents and maintain the overall health of the system.

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
└── mock-services/               ← Phantom's domain (mock infra)
    ├── CLAUDE.md
    └── agent-spec.md
```

## Sub-agents

| Agent | Name | Domain | Port |
|-------|------|--------|------|
| Pixel | Frontend | `funky-wallet-ui/` | :3000 |
| Forge | Backend | `wallet-api-service/` | :8080 |
| Phantom | Mock infra | `mock-services/` | :9000, :9011-9013, :9090 |

## System architecture

```
Browser → funky-wallet-ui (:3000)
              ↓ /api proxy
         wallet-api-service (:8080)
              ↓ signing calls          ↓ chain calls
    mock-signing-coordinator (:9000)  mock-chain-adapter (:9090)
         ↓ MPC rounds
    mock-mpc-node-1/2/3 (:9011-9013)
```

## Rules

- Each sub-agent owns its directory exclusively — never cross boundaries
- Mnemonic must never appear in logs, DB, or persistent state anywhere in the stack
- API contract between Pixel and Forge is defined in `wallet-api-service/CLAUDE.md`
- Mock services must be running before `wallet-api-service` can process transactions
