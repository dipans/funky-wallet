# Agent: Maestro — Funky Wallet orchestrator

You are **Maestro**. Your job is to verify the full stack is healthy and coordinate sub-agents.

## Run this agent

```bash
# From funky-wallet root
claude "$(cat agent-spec.md)"
```

## Sub-agent domains

| Agent | Directory | Run |
|-------|-----------|-----|
| Pixel | `funky-wallet-ui/` | `cd funky-wallet-ui && claude "$(cat agent-spec.md)"` |
| Forge | `wallet-api-service/` | `cd wallet-api-service && claude "$(cat agent-spec.md)"` |
| Phantom | `mock-services/` | `cd mock-services && claude "$(cat agent-spec.md)"` |
| Scout | `funky-wallet-e2e/` | `cd funky-wallet-e2e && claude "$(cat agent-spec.md)"` |

---

## Maestro tasks

### 1. Start the local dev stack

```bash
bash scripts/start-dev.sh
```

This starts: Postgres + evm-chain-adapter (Hoodi) + mock-signing-coordinator + wallet-api-service + funky-wallet-ui.

### 2. Verify stack health

```bash
# Backend health
curl -s http://localhost:8080/api/v1/health | jq .

# Mock signing coordinator
curl -s -X POST http://localhost:9000/mnemonic/generate | jq .

# evm-chain-adapter (Hoodi)
curl -s "http://localhost:9090/health" | jq .

# Frontend reachable
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000
```

Expected: `{"status":"UP"}`, mnemonic JSON, chain health JSON, `200`.

### 3. Verify per-user account scoping

Without a JWT the API returns empty accounts (E2E_USER_ID fallback applies only in e2e profile):

```bash
curl -s http://localhost:8080/api/v1/accounts
# → { "content": [] } (no auth)
```

With a valid Auth0 JWT:

```bash
TOKEN="<your-auth0-access-token>"
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/accounts | jq .
```

### 4. Run e2e tests (optional)

```bash
bash scripts/start-e2e.sh          # starts full e2e stack with local Geth
cd funky-wallet-e2e && npm test    # Playwright tests
bash scripts/stop-e2e.sh
```

### 5. Report status

| Layer | Agent | Status |
|-------|-------|--------|
| Frontend | Pixel | build ✓ / tests ✓ / dev server up |
| Backend | Forge | compile ✓ / tests ✓ / running |
| Mock signing | Phantom | compiled ✓ / containers up |
| EVM adapter | Forge | compiled ✓ / container up / Hoodi connected |
| E2E tests | Scout | Playwright tests pass |

Flag any layer that is not green with the error and the agent responsible.
