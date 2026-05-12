# Forge (evm) — evm-chain-adapter

Extension of Forge's domain. Real Ethereum chain adapter using web3j.
Replaces mock-chain-adapter for local dev and e2e.

## Stack
- Java 21 + Spring Boot 3.2
- web3j 4.10.3 — Ethereum JSON-RPC client
- Connects to Geth node at `${GETH_RPC_URL}` (default http://localhost:8545)
- Chain ID: `${GETH_CHAIN_ID}` (default 1337 for local dev)

## Endpoints (same contract as mock-chain-adapter + block endpoints)

```
GET  /balance?address=&network=          → { amount, symbol }
POST /tx/build    body { from, to, amount, network }  → { unsignedTx }
POST /tx/broadcast body { signedTx, network }         → { txHash }
GET  /health                             → { status, node }
GET  /block/latest                       → { blockNumber, blockHash }
GET  /block/{blockNumber}/transactions   → [{ hash, fromAddress, toAddress, value, blockHash }]
```

The block endpoints are used by `BlockWatcherService` in wallet-api-service to detect incoming transactions.

## Local Geth node (e2e profile)
- chainId: 1337, Clique PoA, 2s blocks
- Pre-funded accounts in geth-dev/genesis.json
- Sealer: 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 (100k ETH)

## Local dev profile
- Points to Hoodi testnet (remote RPC) — no local Geth needed
- Set `GETH_RPC_URL` to your Hoodi RPC endpoint in docker-compose.dev.yml

## TODO (after funky-contracts)
- Add ERC20 token transfer support (read contract address from env)
- Add `eth_call` for `balanceOf` on ERC20

## Commands
```bash
# Compile
./mvnw compile

# Run against local Geth
GETH_RPC_URL=http://localhost:8545 ./mvnw spring-boot:run

# Build Docker image
docker build -t evm-chain-adapter .
```
