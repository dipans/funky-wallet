Run the Funky Wallet e2e test suite against the real Geth node.

## Steps

### 1. Verify the full stack is up

```bash
# All services must be healthy before running tests
curl -sf http://localhost:8080/api/v1/health | jq .
curl -sf -X POST http://localhost:9000/mnemonic/generate -H 'Content-Type: application/json' -d '{}' | jq .mnemonic
curl -sf "http://localhost:9090/balance?address=0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266&network=ETHEREUM" | jq .
cast block-number --rpc-url http://localhost:8545 2>/dev/null || curl -sf -X POST http://localhost:8545 -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' | jq .result
```

If any service is unhealthy, stop and tell the user to run `/funkyup` first.

### 2. Run the Playwright tests

```bash
cd /Users/dipan/MyResources/Projects/funky-wallet/funky-wallet-e2e
npm test 2>&1
```

Pass any arguments the user provided. Examples:
- `/funkytest` → run all tests
- `/funkytest --headed` → run with visible browser
- `/funkytest --grep "send ETH"` → run a specific test

### 3. Report results

After the run, report a summary table:

| Test | Result | Duration |
|------|--------|----------|
| 1. account exists + balance | ✓ / ✗ | Xs |
| 2. create account | ✓ / ✗ | Xs |
| 3. send ETH (real Geth) | ✓ / ✗ | Xs |
| 4. receive ETH | ✓ / ✗ | Xs |
| 5. round-trip | ✓ / ✗ | Xs |

### 4. On failure

If any test fails, show:
1. The test name and assertion that failed
2. The last 20 lines of relevant logs:
```bash
tail -20 /Users/dipan/MyResources/Projects/funky-wallet/.logs/wallet-api-service.log
```
3. A suggested fix based on the error (e.g. "signing coordinator returned 500 — check web3j signing", "transaction not confirmed — Geth may not be mining")

### 5. On success

Print:
```
✓ All 5 e2e tests passed
  Real Geth signing ✓
  Transaction broadcast ✓
  On-chain confirmation ✓
  Balance tracking ✓
```
