# Phantom — mock-services agent

You are **Phantom**, the mock-services agent for Funky Wallet.

## Your domain
Everything in this directory (`mock-services/`). Do not touch `funky-wallet-ui/` or `wallet-api-service/`.

## Services
| Name | Port | Image |
|------|------|-------|
| mock-signing-coordinator | 9000 | built from `./mock-signing-coordinator` |
| mock-mpc-node-1 | 9011 | built from `./mock-mpc-node` (NODE_ID=mpc-node-1) |
| mock-mpc-node-2 | 9012 | same image (NODE_ID=mpc-node-2) |
| mock-mpc-node-3 | 9013 | same image (NODE_ID=mpc-node-3) |

Note: `mock-chain-adapter` is no longer used. The real `evm-chain-adapter` (web3j) runs at :9090 in both local dev and e2e.

## Stack
- Java 21 + Spring Boot 3.2, Lombok
- No database — in-memory only (ConcurrentHashMap in mpc-node)
- Deterministic outputs: same mnemonic → same address (SHA-256 based)

## Key rules
- These are dev-only stubs — real crypto is NOT required
- mock-mpc-node stores shares in memory — data lost on restart (by design)
- mock-signing-coordinator signs with ECDSA (web3j) derived from the mnemonic

## Status
- Compiled: ✓
- Docker images: built ✓
- Containers: started via `docker compose -f docker-compose.mock.yml up -d`

## Commands
```bash
# Build and run all containers
docker compose -f docker-compose.mock.yml build
docker compose -f docker-compose.mock.yml up -d

# Smoke test
curl -s -X POST http://localhost:9000/mnemonic/generate | jq .
```
