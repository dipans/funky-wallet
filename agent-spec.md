# Agent: Maestro — Funky Wallet orchestrator

You are **Maestro**. Your job is to spin up all three sub-agents in parallel and ensure the full stack is healthy.

## Run this agent

```bash
# From funky-wallet root
claude "$(cat agent-spec.md)"
```

## Sub-agent launch commands

Open three terminals and run each agent in its own project directory:

```bash
# Terminal 1 — Pixel (frontend)
cd funky-wallet-ui && claude "$(cat agent-spec.md)"

# Terminal 2 — Forge (backend)
cd wallet-api-service && claude "$(cat agent-spec.md)"

# Terminal 3 — Phantom (mock services)
cd mock-services && claude "$(cat agent-spec.md)"
```

---

## Maestro tasks

### 1. Launch all sub-agents
Spawn Pixel, Forge, and Phantom simultaneously (see commands above).
Each agent reads its own `agent-spec.md` and `CLAUDE.md` for context.

### 2. Verify the full stack comes up

Once all agents report completion, verify end-to-end:

```bash
# Mock services healthy
curl -s -X POST http://localhost:9000/mnemonic/generate | jq .
curl -s "http://localhost:9090/balance?address=0xABC&network=ETHEREUM" | jq .

# Backend healthy
curl -s http://localhost:8080/api/v1/health | jq .

# Frontend reachable
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000
```

Expected: mnemonic JSON, balance JSON, `{"status":"UP"}`, `200`.

### 3. Run the account creation flow end-to-end

```bash
# Create an account via the API
curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"network":"ETHEREUM"}' | jq '{address: .account.address, mnemonic: .mnemonic}'
```

Expected: address string + 12-word mnemonic.

### 4. Report status

Summarise the health of each layer:

| Layer | Agent | Status |
|-------|-------|--------|
| Frontend | Pixel | build ✓ / tests ✓ / dev server up |
| Backend | Forge | compile ✓ / tests ✓ / running |
| Mock signing | Phantom | compiled ✓ / containers up |
| Mock chain | Phantom | compiled ✓ / containers up |
| End-to-end | Maestro | account creation ✓ |

Flag any layer that is not green with the error and the sub-agent responsible for fixing it.
