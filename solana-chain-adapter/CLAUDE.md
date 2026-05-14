# Forge (solana) — solana-chain-adapter

Solana chain adapter for FunkyWallet. Bridges wallet-api-service to Solana devnet/mainnet via JSON-RPC.
Runs alongside evm-chain-adapter; each handles its own network type.

## Stack
- Java 21 + Spring Boot 3.2
- solanaj 1.20.4 — Solana JSON-RPC client + Transaction building
- Port: **9091**
- RPC target: `${SOLANA_RPC_URL}` (default `https://api.devnet.solana.com`)

## Endpoints

```
GET  /balance?address=&network=          → { amount (SOL), symbol: "SOL" }
POST /tx/build   { from, to, amount, network }  → { unsignedTx: "from|to|lamports|blockhash" }
POST /tx/broadcast { signedTx, network }         → { txHash }
POST /account/setup { walletAddress }            → { nonceAccount }
GET  /account/{address}/new-transactions?since={sig}  → [IncomingTx]
GET  /health                             → { status, node }
```

## Transaction signing contract

Unlike EVM (RLP encoding), Solana tx building and signing are split across services:

1. **Chain adapter** (`/tx/build`) fetches recent blockhash and returns structured params:
   `"from|to|lamports|recentBlockhash"` — no cryptography here

2. **Signing coordinator** receives the params, derives the ed25519 key via SLIP-0010,
   builds the `Transaction` using solanaj, signs it, and returns base64 wire format

3. **Chain adapter** (`/tx/broadcast`) sends the base64 wire format directly via
   `sendRawTransaction` with `encoding=base64`

## Nonce account

`POST /account/setup` returns a deterministic nonce account address derived from the
wallet address (`SHA-256("solana-nonce:" + walletAddress)`). In dev this is a placeholder —
no on-chain account is actually created. In production this endpoint should:
1. Create a real nonce account funded with ≥0.00144 SOL (rent-exempt)
2. Call `SystemProgram.initializeNonce(nonceAccount, nonceAuthority=walletAddress)`

## Block watcher endpoint

`GET /account/{address}/new-transactions?since={lastSignature}` is called by
`BlockWatcherService` in wallet-api-service every 15s. It:
- Calls `getSignaturesForAddress(address, limit=50, FINALIZED)`
- Stops iteration when `lastSignature` is encountered (newest-first order)
- For each new signature: fetches `getTransaction`, parses `preBalances`/`postBalances`
- Returns only receives (positive delta for the watched address)

## TODO (production)
- Replace `getLatestBlockhash` in `/tx/build` with durable nonce account state
  so MPC signing rounds don't race the 80-second recent-blockhash expiry
- `/account/setup`: create real nonce account on-chain, not a derived placeholder
- Add SPL token balance support (`getTokenAccountBalance`)

## Commands
```bash
./mvnw compile
SOLANA_RPC_URL=https://api.devnet.solana.com ./mvnw spring-boot:run
docker build -t solana-chain-adapter .
```
