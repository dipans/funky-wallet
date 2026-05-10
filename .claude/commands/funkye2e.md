Bring up the FunkyWallet e2e stack (local Geth) and run the Playwright test suite.

## Steps

### 1. Stop local dev stack if running (avoid port conflicts)
```bash
cd /Users/dipan/MyResources/Projects/funky-wallet
bash scripts/stop-dev.sh 2>/dev/null || true
```

### 2. Start the e2e stack
```bash
bash scripts/start-e2e.sh
```

If start-e2e.sh reports any container unhealthy, stop and tell the user which container failed and show its logs:
```bash
docker logs <container-name> --tail 30
```

### 3. Verify the full stack before running tests
```bash
curl -sf http://localhost:8080/api/v1/health | jq .
curl -sf -X POST http://localhost:9000/mnemonic/generate -H 'Content-Type: application/json' -d '{}' | jq .mnemonic
curl -sf "http://localhost:9090/balance?address=0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266&network=ETHEREUM" | jq .
curl -sf -X POST http://localhost:8545 -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' | jq .result
```

### 4. Run Playwright tests
```bash
cd /Users/dipan/MyResources/Projects/funky-wallet/funky-wallet-e2e
npm test 2>&1
```

Pass any arguments the user provided. Examples:
- `/funkye2e` → run all tests
- `/funkye2e --headed` → run with visible browser
- `/funkye2e --grep "send ETH"` → run a specific test

### 5. Report results

| Test | Result | Duration |
|------|--------|----------|
| 1. account exists + balance | ✓ / ✗ | Xs |
| 2. create account | ✓ / ✗ | Xs |
| 3. send ETH (real Geth) | ✓ / ✗ | Xs |
| 4. receive ETH | ✓ / ✗ | Xs |
| 5. round-trip | ✓ / ✗ | Xs |

### 6. On failure
Show the failing test name and assertion, then check:
```bash
docker logs funkywallet-signing --tail 20
docker logs funkywallet-evm-adapter --tail 20
docker logs funkywallet-api-e2e --tail 20
docker logs funkywallet-geth --tail 20
```

### 7. On success
```
✓ All 5 e2e tests passed
  Real Geth signing ✓
  Transaction broadcast ✓
  On-chain confirmation ✓
  Balance tracking ✓
```
