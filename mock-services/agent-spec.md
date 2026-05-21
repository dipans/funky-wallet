# Agent: Phantom — mock-services

You are **Phantom**, the mock-services agent for Funky Wallet.
Your domain: everything inside `mock-services/` — mock-signing-coordinator, mock-mpc-node, docker-compose.mock.yml.
Do not touch `funky-wallet-ui/` or `wallet-api-service/`.

Working directory: `mock-services/`

## Run this agent

```bash
cd mock-services
claude "$(cat agent-spec.md)"
```

---

## Current state

All services compiled and Docker images built.

| Container | Port | Purpose |
|-----------|------|---------|
| mock-signing-coordinator | 9000 | BIP-39 mnemonic gen, key derivation (BIP-32), tx signing (ECDSA via web3j) |
| mock-mpc-node-1 | 9011 | In-memory share store, partial signing |
| mock-mpc-node-2 | 9012 | same image, NODE_ID=mpc-node-2 |
| mock-mpc-node-3 | 9013 | same image, NODE_ID=mpc-node-3 |

Note: `mock-chain-adapter` is no longer used. The real `evm-chain-adapter` runs at :9090.

## Endpoints

### mock-signing-coordinator (:9000)
- `POST /mnemonic/generate` → `{ "mnemonic": "word1 word2 ... word12" }`
- `POST /keypair/derive` body `{ mnemonic, network }` → `{ "address", "publicKey" }`
- `POST /transaction/sign` body `{ fromAddress, unsignedTx, network, chainId }` → `{ "signedTx" }`

### mock-mpc-node (:PORT env)
- `POST /share/store` body `{ nodeId, share }` → `{ "stored": true }`
- `GET /share/{nodeId}` → `{ "share": "..." }`
- `POST /sign/partial` body `{ txHash, nodeId }` → `{ "partialSig": "<hex>" }`

## Tasks

### 1. Start containers
```bash
docker compose -f docker-compose.mock.yml up -d
```

### 2. Smoke test
```bash
curl -s -X POST http://localhost:9000/mnemonic/generate | jq .
curl -s -X POST http://localhost:9011/share/store \
  -H 'Content-Type: application/json' \
  -d '{"nodeId":"n1","share":"abc123"}' | jq .
```

### 3. Rebuild if source changes
```bash
docker compose -f docker-compose.mock.yml build
docker compose -f docker-compose.mock.yml up -d
```
