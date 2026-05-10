Bring up the full FunkyWallet development ecosystem with test data.

## Steps

1. Run the startup script:
```bash
cd /Users/dipan/MyResources/Projects/funky-wallet
bash scripts/start-dev.sh
```

2. Once the script completes, verify every layer is healthy by checking these endpoints:

```bash
# Backend health
curl -s http://localhost:8080/api/v1/health | jq .

# Test account exists in DB
curl -s http://localhost:8080/api/v1/accounts/0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 | jq '{address: .address, network: .network}'

# Test transactions seeded (expect 3)
curl -s "http://localhost:8080/api/v1/transactions?address=0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266" | jq '.totalElements'

# Mock signing coordinator
curl -s -X POST http://localhost:9000/keypair/derive \
  -H 'Content-Type: application/json' \
  -d '{"mnemonic":"test test test test test test test test test test test junk","network":"ETHEREUM"}' | jq .

# Mock chain adapter
curl -s "http://localhost:9090/balance?address=0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266&network=ETHEREUM" | jq .
```

3. Report a status table with one row per layer:

| Layer | URL | Status | Notes |
|-------|-----|--------|-------|
| funky-wallet-ui | http://localhost:3000 | ✓/✗ | |
| wallet-api-service | http://localhost:8080 | ✓/✗ | |
| mock-signing-coordinator | http://localhost:9000 | ✓/✗ | |
| mock-chain-adapter | http://localhost:9090 | ✓/✗ | |
| Postgres | localhost:5432 | ✓/✗ | |

4. If any layer is unhealthy, check its log file in `.logs/` and report the last 20 lines of the relevant log with a suggested fix.

5. If all layers are healthy, print the test credentials:
```
Test account:  0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266
Test mnemonic: test test test test test test test test test test test junk
Frontend:      http://localhost:3000
Swagger:       http://localhost:8080/swagger-ui.html
Adminer:       http://localhost:8888
```
