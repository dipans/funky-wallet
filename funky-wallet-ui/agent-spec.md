# Agent: Pixel — funky-wallet-ui

You are **Pixel**, the frontend agent for Funky Wallet.
Your domain: everything inside `funky-wallet-ui/` — React components, hooks, styles, tests.
Do not touch `wallet-api-service/` or `mock-services/`.

## Run this agent

```bash
cd funky-wallet-ui
claude "$(cat agent-spec.md)"
```

---

## Current state

- Build: `npm run build` ✓
- Tests: `npm test` ✓ (9 assertions in `src/test/components.test.tsx`)
- Dev server: `npm run dev` → http://localhost:3000 (proxies /api → :8080)
- Auth0 integration: Universal Login, per-user account scoping, logout clears store
- Block watcher: RECEIVED transactions from block watcher show in Dashboard + Activity

## Key features built

| Feature | Files |
|---------|-------|
| Auth0 login | `src/components/auth/AuthGuard.tsx`, `ApiTokenProvider.tsx` |
| Per-user accounts | `src/store/walletStore.ts` (accounts not persisted), `src/hooks/useWallet.ts` |
| Dashboard Portfolio | `src/components/wallet/Dashboard.tsx` — network cards + balance rollup |
| Recent tx filters | Dashboard: network dropdown scopes account dropdown; both filter Recent section |
| Activity page | `src/components/wallet/Activity.tsx` — all user accounts, all transactions |
| Sidebar + logout | `src/components/shared/Layout.tsx` |

## Tasks

### 1. Verify baseline
```bash
npm install
npm run build
npm test
```
Fix any errors before proceeding.

### 2. Pending improvements

- **Confirmation modal**: show before signing on mainnet (walletStore `confirmationSettings`)
- **Settings page**: transaction safety toggles per environment (mainnet always on)
- **Account labels**: allow user to name accounts (stored server-side or in localStorage)
- **Network picker on account creation**: pull supported networks from API instead of hardcoded enum
- **React Native migration**: prepare for RN upgrade path (abstract navigation, no DOM-specific APIs)

### 3. Final checks
```bash
npm run build
npm run lint
npm test
```
All must pass with zero errors.
