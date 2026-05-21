Bring up the full FunkyWallet local development stack: Postgres + EVM adapter (Hoodi) + Solana adapter (devnet) + mock signing + wallet-api-service + funky-wallet-ui.

## Steps

### 1. Run the startup script
```bash
cd /Users/dipan/MyResources/Projects/funky-wallet
bash scripts/start-dev.sh
```

### 2. Verify every layer is healthy

```bash
# Backend health
curl -sf http://localhost:8080/api/v1/health | jq .

# Signing coordinator
curl -sf -X POST http://localhost:9000/mnemonic/generate -H 'Content-Type: application/json' -d '{}' | jq .mnemonic

# EVM chain adapter (Hoodi testnet)
curl -sf "http://localhost:9090/balance?address=0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266&network=ETHEREUM" | jq .

# Solana chain adapter (devnet)
curl -sf "http://localhost:9091/health" | jq .

# Frontend reachable
curl -sf -o /dev/null -w "%{http_code}" http://localhost:3000 && echo " OK"
```

### 3. Report a status table

| Layer | URL | Status | Notes |
|-------|-----|--------|-------|
| funky-wallet-ui | http://localhost:3000 | ✓/✗ | |
| wallet-api-service | http://localhost:8080 | ✓/✗ | |
| mock-signing-coordinator | http://localhost:9000 | ✓/✗ | |
| evm-chain-adapter (Hoodi) | http://localhost:9090 | ✓/✗ | |
| solana-chain-adapter (devnet) | http://localhost:9091 | ✓/✗ | |
| Postgres | localhost:5432 | ✓/✗ | via Docker |
| Adminer | http://localhost:8888 | ✓/✗ | DB UI |

### 4. On failure
Check `.logs/` for the relevant service and report the last 20 lines with a suggested fix.

### 5. On success
```
Frontend:      http://localhost:3000
Swagger:       http://localhost:8080/swagger-ui.html
Adminer:       http://localhost:8888  (funky / funky / funkywallet_dev)
EVM chain:     Hoodi testnet (chainId 560048)
Solana chain:  Devnet
Block watcher: polls every 15s (EVM by block, Solana by address)
```
