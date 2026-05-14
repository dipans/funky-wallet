# Funky Wallet — Architecture

Funky Wallet is a non-custodial MPC wallet where private keys are split across multiple nodes and never reconstructed in one place. The mnemonic (BIP-39) is the user's recovery phrase — shown once and never stored.

---

## 1. System Overview

```mermaid
graph TB
    subgraph "User"
        Browser["Browser / App"]
    end

    subgraph "Auth"
        Auth0["Auth0\n(Universal Login)"]
    end

    subgraph "Funky Wallet"
        UI["funky-wallet-ui\n:3000\nReact SPA"]
        API["wallet-api-service\n:8080\nJava Spring Boot"]
        DB[("PostgreSQL\n:5432")]

        subgraph "Signing (MPC)"
            SC["mock-signing-coordinator\n:9000"]
            MPC1["mock-mpc-node-1\n:9011"]
            MPC2["mock-mpc-node-2\n:9012"]
            MPC3["mock-mpc-node-3\n:9013"]
        end

        subgraph "Chain Adapters"
            EVM["evm-chain-adapter\n:9090\nweb3j"]
            SOL["solana-chain-adapter\n:9091\nsolanaj"]
        end
    end

    subgraph "Blockchains"
        Hoodi["Hoodi Testnet\n(EVM)"]
        DevNet["Solana Devnet"]
        Geth["Local Geth\n(e2e only)"]
    end

    Browser -- "HTTPS" --> Auth0
    Browser -- "HTTPS" --> UI
    UI -- "Bearer JWT\n/api proxy" --> API
    API -- "JPA" --> DB
    API -- "HTTP" --> SC
    API -- "HTTP" --> EVM
    API -- "HTTP" --> SOL
    SC -- "HTTP (MPC rounds)" --> MPC1
    SC -- "HTTP (MPC rounds)" --> MPC2
    SC -- "HTTP (MPC rounds)" --> MPC3
    EVM -- "JSON-RPC" --> Hoodi
    EVM -- "JSON-RPC" --> Geth
    SOL -- "JSON-RPC" --> DevNet
```

---

## 2. Component Detail

```mermaid
graph LR
    subgraph "funky-wallet-ui :3000"
        AuthGuard["AuthGuard\n(Auth0 redirect)"]
        ApiToken["ApiTokenProvider\n(injects Bearer)"]
        Store["Zustand Store\n(accounts, activeAccount)\nnot persisted to localStorage"]
        Dashboard["Dashboard\nPortfolio + Recent Txs\nnetwork/account filters"]
        Activity["Activity\nall user txs"]
        Send["Send\nform + mnemonic input"]
    end

    subgraph "wallet-api-service :8080"
        JWTFilter["JwtAuthenticationFilter\nextract sub → SecurityContext"]
        AccSvc["AccountService\nuserId-scoped CRUD"]
        TxSvc["TransactionService\nuserId-scoped + paginated"]
        EVMWatcher["BlockWatcherService\nwatchBlocks()\nEVM block iteration"]
        SolWatcher["BlockWatcherService\nwatchSolanaAccounts()\nper-address signatures"]
        ChainClient["ChainAdapterClient\nroutes by networkType\nEVM→:9090 Solana→:9091"]
        SignClient["SigningCoordinatorClient"]
    end

    subgraph "mock-signing-coordinator :9000"
        Vault["MnemonicVault\nAES-256-GCM encrypted\nin-memory"]
        EVMSign["EVM signing\nBIP-32 secp256k1\nm/44'/60'/0'/0/0"]
        SolSign["Solana signing\nSLIP-0010 ed25519\nm/44'/501'/0'/0'"]
    end

    subgraph "evm-chain-adapter :9090"
        Web3j["web3j\nJSON-RPC client"]
    end

    subgraph "solana-chain-adapter :9091"
        Solanaj["solanaj\nJSON-RPC client"]
    end

    AuthGuard --> ApiToken --> Store
    Dashboard & Activity & Send --> Store
    Dashboard & Activity & Send -- "axios + Bearer" --> JWTFilter
    JWTFilter --> AccSvc & TxSvc
    AccSvc --> ChainClient & SignClient
    TxSvc --> ChainClient & SignClient
    EVMWatcher --> ChainClient
    SolWatcher --> ChainClient
    ChainClient --> Web3j & Solanaj
    SignClient --> Vault
    Vault --> EVMSign & SolSign
```

---

## 3. Database Schema

```mermaid
erDiagram
    accounts {
        UUID id PK
        VARCHAR address UK
        VARCHAR publicKey
        VARCHAR network
        INTEGER chainId
        VARCHAR chainName
        VARCHAR networkType
        VARCHAR environment
        VARCHAR userId
        TEXT chain_details "JSON — Solana: nonceAccount + nonceAuthority"
        TIMESTAMP createdAt
    }

    transactions {
        UUID id PK
        VARCHAR hash UK
        VARCHAR fromAddress
        VARCHAR toAddress
        DECIMAL amount
        VARCHAR symbol
        VARCHAR network
        VARCHAR status "PENDING|CONFIRMED|FAILED|RECEIVED"
        VARCHAR blockHash
        TIMESTAMP createdAt
        TIMESTAMP confirmedAt
    }

    block_sync_state {
        UUID id PK
        VARCHAR network UK "EVM network name e.g. ETHEREUM"
        BIGINT lastProcessedBlock
        VARCHAR lastProcessedBlockHash
        TIMESTAMP updatedAt
    }

    solana_sync_state {
        VARCHAR address PK "Solana wallet address"
        VARCHAR lastSignature "most recent finalized sig processed"
        TIMESTAMP updatedAt
    }
```

### `chain_details` JSON by network

| Network | Value |
|---------|-------|
| EVM | `null` |
| Solana | `{"nonceAccount":"<base58>","nonceAuthority":"<base58>"}` |
| Bitcoin (future) | `{"xpub":"...","addressType":"p2wpkh"}` |

---

## 4. Authentication Flow

```mermaid
sequenceDiagram
    actor User
    participant UI as funky-wallet-ui
    participant Auth0
    participant API as wallet-api-service
    participant Istio as Istio Sidecar (prod)

    User->>UI: navigate to app
    UI->>Auth0: redirect to Universal Login
    Auth0-->>User: login page
    User->>Auth0: credentials
    Auth0-->>UI: JWT access token (sub = userId)
    UI->>UI: ApiTokenProvider stores token getter
    User->>UI: any action (e.g. view accounts)
    UI->>API: GET /api/v1/accounts\nAuthorization: Bearer <JWT>
    Note over Istio: prod: validates JWT signature\n(RequestAuthentication + DENY-all policy)
    API->>API: JwtAuthenticationFilter\ndecodes payload (no sig verify)\nextracts sub → SecurityContext
    API-->>UI: Account[] filtered by userId
```

---

## 5. EVM Account Creation

```mermaid
sequenceDiagram
    participant UI as funky-wallet-ui
    participant API as wallet-api-service
    participant SC as mock-signing-coordinator
    participant DB as PostgreSQL

    UI->>API: POST /api/v1/accounts\n{ network, chainId, chainName, networkType }
    API->>SC: POST /mnemonic/generate
    SC-->>API: { mnemonic }
    API->>SC: POST /keypair/derive\n{ mnemonic, network: "ETHEREUM" }
    Note over SC: BIP-39 seed → BIP-32\nm/44'/60'/0'/0/0\nsecp256k1 → Ethereum address
    SC->>SC: vault.store(address, mnemonic)
    SC-->>API: { address, publicKey }
    API->>DB: INSERT accounts (address, userId, networkType, ...)\nchain_details = null (EVM)
    API-->>UI: { account, mnemonic }
    UI->>UI: show mnemonic once\nclear from memory
```

---

## 6. Solana Account Creation

```mermaid
sequenceDiagram
    participant UI as funky-wallet-ui
    participant API as wallet-api-service
    participant SC as mock-signing-coordinator
    participant SOL as solana-chain-adapter
    participant DB as PostgreSQL

    UI->>API: POST /api/v1/accounts\n{ network: SOLANA, chainId: 0, ... }
    API->>SC: POST /mnemonic/generate
    SC-->>API: { mnemonic }
    API->>SC: POST /keypair/derive\n{ mnemonic, network: "SOLANA" }
    Note over SC: BIP-39 seed → SLIP-0010\nm/44'/501'/0'/0'\ned25519 → base58 address
    SC->>SC: vault.store(address, mnemonic)
    SC-->>API: { address, publicKey }
    API->>SOL: POST /account/setup\n{ walletAddress }
    Note over SOL: SHA-256("solana-nonce:"+addr)\n→ deterministic nonce account address\n(prod: create real on-chain nonce account)
    SOL-->>API: { nonceAccount }
    API->>DB: INSERT accounts\nchain_details = {"nonceAccount":"...","nonceAuthority":"<walletAddr>"}
    API-->>UI: { account, mnemonic }
```

---

## 7. Send Transaction (EVM)

```mermaid
sequenceDiagram
    participant UI as funky-wallet-ui
    participant API as wallet-api-service
    participant EVM as evm-chain-adapter
    participant SC as mock-signing-coordinator
    participant Chain as Hoodi / Geth

    UI->>API: POST /api/v1/transactions\n{ fromAddress, toAddress, amount, network, mnemonic }
    API->>API: verify fromAddress belongs to current userId
    API->>EVM: POST /tx/build\n{ from, to, amount, network }
    EVM->>Chain: ethGetTransactionCount (nonce)
    EVM->>Chain: ethGasPrice
    Chain-->>EVM: nonce, gasPrice
    EVM-->>API: { unsignedTx: "from|0xRLPencoded" }
    API->>SC: POST /transaction/sign\n{ accountAddress, unsignedTx, network, chainId }
    Note over SC: vault.decrypt(address) → mnemonic\nBIP-32 derive credentials\nEIP-155 sign with chainId
    SC-->>API: { signedTx: "0xsignedRLP" }
    API->>EVM: POST /tx/broadcast\n{ signedTx, network }
    EVM->>Chain: eth_sendRawTransaction
    Chain-->>EVM: txHash
    EVM-->>API: { txHash }
    API->>API: save tx PENDING\nasync confirmTransactionAsync (3s delay)
    API-->>UI: { tx: PENDING, hash }
    Note over API: async: set CONFIRMED after 3s
```

---

## 8. Send Transaction (Solana)

```mermaid
sequenceDiagram
    participant UI as funky-wallet-ui
    participant API as wallet-api-service
    participant SOL as solana-chain-adapter
    participant SC as mock-signing-coordinator
    participant Chain as Solana Devnet

    UI->>API: POST /api/v1/transactions\n{ fromAddress, toAddress, amount, network: SOLANA, mnemonic }
    API->>SOL: POST /tx/build\n{ from, to, amount, network }
    SOL->>Chain: getLatestBlockhash
    Chain-->>SOL: blockhash
    Note over SOL: returns params string:\n"from|to|lamports|blockhash"\n(no crypto here)
    SOL-->>API: { unsignedTx: "from|to|lamports|blockhash" }
    API->>SC: POST /transaction/sign\n{ accountAddress, unsignedTx, network: SOLANA }
    Note over SC: SLIP-0010 derive ed25519 key\nsolanaj: build Transaction\n(SystemProgram.transfer)\nsign → base64 wire format
    SC-->>API: { signedTx: "<base64 wire format>" }
    API->>SOL: POST /tx/broadcast\n{ signedTx, network }
    SOL->>Chain: sendRawTransaction (encoding=base64)
    Chain-->>SOL: signature (txHash)
    SOL-->>API: { txHash }
    API-->>UI: { tx: PENDING, hash }
```

---

## 9. EVM Incoming Transaction Detection

```mermaid
sequenceDiagram
    participant Sched as @Scheduled (15s)
    participant BW as BlockWatcherService
    participant EVM as evm-chain-adapter
    participant Chain as Hoodi / Geth
    participant DB as PostgreSQL

    Sched->>BW: watchBlocks()
    BW->>DB: SELECT all account addresses
    BW->>EVM: GET /block/latest
    EVM->>Chain: eth_getBlockByNumber(LATEST)
    Chain-->>EVM: { blockNumber, blockHash }
    EVM-->>BW: { blockNumber, blockHash }
    BW->>DB: SELECT block_sync_state WHERE network='ETHEREUM'
    loop for each new block (lastProcessed+1 → latest)
        BW->>EVM: GET /block/{n}/transactions
        EVM->>Chain: eth_getBlockByNumber(n, fullTxs=true)
        Chain-->>EVM: [{ hash, from, to, value }]
        EVM-->>BW: [TxInfo]
        loop for each tx where toAddress in watchedAddresses
            BW->>DB: INSERT transaction\n(status=RECEIVED, blockHash)
        end
        BW->>DB: UPDATE block_sync_state\n(lastProcessedBlock=n)
    end
```

---

## 10. Solana Incoming Transaction Detection

```mermaid
sequenceDiagram
    participant Sched as @Scheduled (15s)
    participant BW as BlockWatcherService
    participant SOL as solana-chain-adapter
    participant Chain as Solana Devnet
    participant DB as PostgreSQL

    Sched->>BW: watchSolanaAccounts()
    BW->>DB: SELECT addresses WHERE networkType='SOLANA'
    loop for each Solana address
        BW->>DB: SELECT solana_sync_state WHERE address=?
        Note over DB: returns lastSignature (null = first run)
        BW->>SOL: GET /account/{addr}/new-transactions?since={lastSig}
        SOL->>Chain: getSignaturesForAddress(addr, limit=50, FINALIZED)
        Chain-->>SOL: [signatures, newest-first]
        loop for each sig (stop at lastSig)
            SOL->>Chain: getTransaction(sig)
            Chain-->>SOL: { accountKeys, preBalances, postBalances }
            Note over SOL: delta = postBal[ourIdx] - preBal[ourIdx]\nskip if delta <= 0 (not a receive)
        end
        SOL-->>BW: [IncomingTx{ sig, from, to, amount, blockTime }]
        loop for each IncomingTx
            BW->>DB: INSERT transaction\n(status=RECEIVED, confirmedAt=blockTime)
        end
        BW->>DB: UPDATE solana_sync_state\n(lastSignature = newest sig seen)
    end
```

---

## 11. Key Design Decisions

### MPC / Non-custodial model
- Private key material is derived from the mnemonic at signing time and immediately discarded
- The mnemonic is stored in-memory only (AES-256-GCM encrypted, keyed per address) in the signing coordinator
- The mnemonic is never persisted to disk, logs, or database at any layer
- In production this coordinator becomes a real MPC cluster (Shamir / TSS)

### JWT validation placement
- JWT signature verification is NOT in the app — Istio's `RequestAuthentication` owns it in production
- The app only base64-decodes the JWT payload to extract the `sub` claim (userId)
- `AnonymousAuthenticationToken` is treated as null userId (no identity), not as a user

### Network-specific account data
- The `accounts` table is network-agnostic
- Network-specific fields live in `chain_details TEXT` (JSON) — null for EVM, populated for Solana
- This pattern extends to Bitcoin (xpub, addressType) without new migrations

### Solana durable nonce (production TODO)
- Current: `buildUnsignedTx` uses `getLatestBlockhash` (expires ~80s)
- Required for MPC: replace with durable nonce account so signing rounds don't race expiry
- The `chain_details.nonceAccount` stores the nonce account address for this purpose
- The first instruction in a durable nonce tx MUST be `SystemProgram.nonceAdvance` (protocol rule)

### Block watcher strategy by chain
| Chain | Strategy | State |
|-------|----------|-------|
| EVM | Iterate new blocks by number | `block_sync_state.lastProcessedBlock` per network |
| Solana | Poll signatures per address | `solana_sync_state.lastSignature` per address |
| Bitcoin (future) | Poll UTXOs or use address subscriptions | TBD |
