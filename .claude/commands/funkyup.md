Bring up the FunkyWallet local development stack (Postgres + evm-chain-adapter → Hoodi testnet + mock signing + API + UI).

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

# Test account in DB with Hoodi chain metadata
curl -sf http://localhost:8080/api/v1/accounts/0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 | jq '{address, chainName, chainId, environment}'

# Real Hoodi on-chain balance (via evm-chain-adapter → Hoodi RPC)
curl -sf "http://localhost:8080/api/v1/accounts/0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266/balance" | jq .

# Signing coordinator
curl -sf -X POST http://localhost:9000/mnemonic/generate -H 'Content-Type: application/json' -d '{}' | jq .mnemonic

# evm-chain-adapter (Hoodi)
curl -sf "http://localhost:9090/balance?address=0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266&network=ETHEREUM" | jq .
```

### 3. Report a status table

| Layer | URL | Status | Notes |
|-------|-----|--------|-------|
| funky-wallet-ui | http://localhost:3000 | ✓/✗ | |
| wallet-api-service | http://localhost:8080 | ✓/✗ | |
| mock-signing-coordinator | http://localhost:9000 | ✓/✗ | |
| evm-chain-adapter (Hoodi) | http://localhost:9090 | ✓/✗ | |
| Postgres | localhost:5432 | ✓/✗ | |

### 4. On failure
Check `.logs/` for the relevant service and report the last 20 lines with a suggested fix.

### 5. On success
```
Test account:  0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266
Test mnemonic: test test test test test test test test test test test junk
Frontend:      http://localhost:3000
Swagger:       http://localhost:8080/swagger-ui.html
Adminer:       http://localhost:8888  (funky / funky)
Chain:         Hoodi testnet (chainId 560048)
```

Note: the test account has minimal Hoodi ETH. Faucet at https://hoodi-faucet.pk910.de if needed.
