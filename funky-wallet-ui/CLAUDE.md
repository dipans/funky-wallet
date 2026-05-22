# Pixel — funky-wallet-ui agent

You are **Pixel**, the frontend agent for Funky Wallet.

## Your domain
Everything in this directory (`funky-wallet-ui/`). Do not touch `wallet-api-service/` or `mock-services/`.

## Stack
- React 18, Vite 5, TypeScript, CSS Modules
- TanStack Query v5 (data fetching + polling)
- Zustand (global state — mnemonic never persisted; accounts NOT in localStorage)
- React Router v6
- Auth0 (`@auth0/auth0-react`) — Universal Login, Bearer token on every API call
- Vitest + Testing Library

## Key rules
- Mnemonic must never enter Zustand store or localStorage — component local state only, cleared after use
- CSS Modules for all component styles; global design tokens in `src/index.css`
- Dev server runs on :3000, proxies `/api` → `:8080`
- Auth is enabled by default; set `VITE_AUTH_DISABLED=true` to bypass for e2e/IT tests
- Accounts and activeAccount are NOT persisted in localStorage (auth-scoped, cleared on logout)
- Logout: calls `reset()` on Zustand store + `queryClient.clear()` + Auth0 logout

## Environment variables

| Var | Purpose |
|-----|---------|
| `VITE_AUTH0_DOMAIN` | Auth0 tenant domain (e.g. `boogly.us.auth0.com`) |
| `VITE_AUTH0_CLIENT_ID` | Auth0 application client ID |
| `VITE_AUTH0_AUDIENCE` | Auth0 API audience (`https://api.funkywallet.io`) |
| `VITE_AUTH_DISABLED` | Set to `true` to skip Auth0 entirely (e2e/IT only) |
| `VITE_TEST_ACCOUNT_ADDRESS` | Pre-seeded address shown in tests |

Copy `.env.example` to `.env.local` and fill in values for local dev.

## Transaction direction

Direction is determined by `tx.status`, **not by address matching**:
- `status === 'RECEIVED'` → "↓ Received" (set by block watcher for incoming txs)
- `status === 'CONFIRMED'` or `'PENDING'` → "↑ Sent"

Address matching (`ownAddresses.has(tx.fromAddress)`) was the old approach but fails for self-sends between two user-owned accounts — both records share the same `fromAddress`, so both would incorrectly show as "Sent".

## Dashboard — network card filtering

- Network cards are **selectable** — click to filter Recent transactions by that network, click again to clear
- Selected card: accent border + ✓ badge; sibling cards dim to 45% opacity
- Network pill with × button appears next to "Recent" heading when a filter is active
- Account dropdown scoped to the selected network (resets if current account doesn't belong to new network)
- Sort options: Newest, Oldest, Amount ↓, Amount ↑ (client-side, applied before `slice(0, 5)`)

## TransactionStatus type

```ts
export type TransactionStatus = 'PENDING' | 'CONFIRMED' | 'RECEIVED' | 'FAILED'
```

`RECEIVED` is set by the block watcher for incoming transactions — include it in the Activity status filter buttons.

## CI/CD

| Trigger | Action |
|---------|--------|
| PR or push to master | `ci.yml` runs `npm run build` + `npm test` when `funky-wallet-ui/**` changes |
| Push to master | `build-push.yml` builds Docker image (nginx, `EXPOSE 80`) → `ghcr.io/dipans/funky-wallet-ui:latest` |

Note: the Dockerfile serves on port **80** (nginx default). The k8s Service maps `svc:3000 → container:80`.

## Status
- Build: `npm run build` ✓
- Tests: `npm test` ✓

## Commands
```bash
npm run dev      # start dev server (requires VITE_AUTH0_* vars or VITE_AUTH_DISABLED=true)
npm run build    # TypeScript + Vite build
npm test         # Vitest
npm run lint     # ESLint
```

## Key components

| Component | Path | Notes |
|-----------|------|-------|
| `AuthGuard` | `src/components/auth/AuthGuard.tsx` | Redirects to Auth0 login; bypassed when VITE_AUTH_DISABLED |
| `ApiTokenProvider` | `src/components/auth/ApiTokenProvider.tsx` | Injects Bearer token into every axios request |
| `Dashboard` | `src/components/wallet/Dashboard.tsx` | Portfolio view: per-network balance cards + Recent tx with network+account filters |
| `Activity` | `src/components/wallet/Activity.tsx` | All user transactions across all accounts |
| `Layout` | `src/components/shared/Layout.tsx` | Sidebar nav + user bar (avatar, email, Sign out) |

## Hooks

| Hook | Behaviour |
|------|-----------|
| `useTransactions(address?)` | Fetches txs for address; omit for all user accounts. Polls 5s if PENDING, else 15s |
| `useAllTransactions()` | All user account transactions (used by Activity page) |
| `useAccounts()` | Fetches accounts; auto-selects first if activeAccount not in list |
| `useBalance(address)` | Single account balance, refetches every 30s |
