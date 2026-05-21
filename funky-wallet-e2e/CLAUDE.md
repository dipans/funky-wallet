# Scout — funky-wallet-e2e agent

You are **Scout**, the e2e test agent for Funky Wallet.
Your domain: everything inside `funky-wallet-e2e/` — Playwright tests, test utilities, fixtures.
Do not touch any other project directory.

## Stack
- Playwright 1.44 + TypeScript
- viem 2.x — Ethereum interactions with local Geth node
- Raw Solana JSON-RPC (fetch-based) — no @solana/web3.js (ESM/CJS conflict)
- Auth is disabled in e2e Docker stack (`VITE_AUTH_DISABLED=true`) — `E2E_USER_ID=e2e|test-user` used as identity
- Local dev: Auth0 is enabled; API-level assertions work, UI assertions skipped

## Test suites

### EVM — `tests/account-lifecycle.spec.ts`
Requires local Geth node + full e2e Docker stack (`bash scripts/start-e2e.sh`).

| Test | What |
|------|------|
| 1 | Test account exists, real ETH balance, shows in UI |
| 2 | Create fresh account — valid 0x address, 12-word mnemonic |
| 3 | Send ETH — real signing → Geth broadcast → CONFIRMED |
| 4 | Receive ETH — fund from secondary, balance increases |
| 5 | Round trip — send + receive, net balance tracked |

### Solana — `tests/solana-lifecycle.spec.ts`
Requires wallet-api-service + solana-chain-adapter + Solana devnet reachable.
Runs against local dev stack — no full e2e Docker needed for API assertions.

| Test | What |
|------|------|
| 1 | Create Solana account — valid base58 address, chainDetails (nonceAccount + nonceAuthority), DEVNET env |
| 2 | New account starts with zero SOL balance |
| 3 | Funded address has SOL on-chain and via chain adapter API |
| 4 | Block watcher recorded incoming SOL as RECEIVED (getSignaturesForAddress) |
| 5 | Airdrop to fresh address — self-skips if devnet rate-limited |

## Prerequisites by suite

| Suite | Geth | EVM adapter | Solana adapter | Signing | API | UI |
|-------|------|-------------|----------------|---------|-----|----|
| EVM | ✓ | ✓ | — | ✓ | ✓ | ✓ |
| Solana | — | — | ✓ | ✓ | ✓ | optional |

```bash
bash scripts/start-dev.sh          # for Solana tests (local dev)
bash scripts/start-e2e.sh          # for EVM tests (full Docker stack)
```

## Test accounts

| Account | Address | Notes |
|---------|---------|-------|
| EVM e2e | `0xae6e338abeeda17b762e846b061ac67b880201ca` | Seeded via Liquibase e2e context |
| Geth funder | `0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266` | 100k ETH in genesis |
| Geth mnemonic | `test test test test test test test test test test test junk` | Hardhat default |
| Solana funded | `SOLANA_TEST_ADDRESS` env (default: `2hKLTVJxDQGVSEqno58jdnMum93Br9xYH4QagpNTKE6r`) | Must exist in DB + have devnet SOL |

## Commands
```bash
npm install
npx playwright install chromium
npm test                                        # all tests
npx playwright test tests/account-lifecycle     # EVM only
npx playwright test tests/solana-lifecycle      # Solana only
SOLANA_TEST_ADDRESS=<addr> npx playwright test tests/solana-lifecycle
npm run test:headed                             # visible browser
npm run test:ui                                 # Playwright UI mode
```

## Environment variables

| Var | Default | Purpose |
|-----|---------|---------|
| `UI_URL` | `http://localhost:3000` | funky-wallet-ui base URL |
| `API_URL` | `http://localhost:8080/api/v1` | wallet-api-service base URL |
| `GETH_RPC_URL` | `http://localhost:8545` | Geth JSON-RPC (EVM tests) |
| `GETH_CHAIN_ID` | `1337` | Chain ID (EVM tests) |
| `SOLANA_RPC_URL` | `https://api.devnet.solana.com` | Solana RPC (Solana tests) |
| `SOLANA_ADAPTER_URL` | `http://localhost:9091` | solana-chain-adapter health check |
| `SOLANA_TEST_ADDRESS` | `2hKLTVJx...` | Pre-funded Solana address for block watcher test |

## Utilities

| File | Purpose |
|------|---------|
| `utils/geth.ts` | viem client, getEthBalance, sendEth, waitForGethTx |
| `utils/solana.ts` | Raw Solana RPC: getSolBalance, airdropSol, waitForSolBalance, waitForSolanaTx |
| `utils/api.ts` | Typed wrappers for wallet-api-service REST API + waitForConfirmed + waitForReceived |
| `fixtures/stack.ts` | EVM fixture — verifies API + Geth + test account healthy |
| `fixtures/solana-stack.ts` | Solana fixture — verifies API + solana-chain-adapter + devnet reachable |
