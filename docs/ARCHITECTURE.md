# Funky Wallet — Architecture

Funky Wallet is a non-custodial MPC wallet where private keys are split across multiple nodes and never reconstructed in one place. The mnemonic (BIP-39) is the user's recovery phrase — shown once and never stored.

---

## 1. System Overview

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#f0f4ff', 'lineColor': '#666'}}}%%
graph TB
    classDef user      fill:#4A90D9,stroke:#2C6FAC,color:#fff,font-weight:bold
    classDef auth      fill:#9B59B6,stroke:#7D3C98,color:#fff,font-weight:bold
    classDef frontend  fill:#27AE60,stroke:#1E8449,color:#fff,font-weight:bold
    classDef backend   fill:#E67E22,stroke:#CA6F1E,color:#fff,font-weight:bold
    classDef db        fill:#E74C3C,stroke:#CB4335,color:#fff,font-weight:bold
    classDef signing   fill:#F39C12,stroke:#D68910,color:#fff,font-weight:bold
    classDef adapter   fill:#1ABC9C,stroke:#17A589,color:#fff,font-weight:bold
    classDef chain     fill:#7F8C8D,stroke:#566573,color:#fff,font-weight:bold

    Browser["Browser / App"]:::user
    Auth0["Auth0\nUniversal Login"]:::auth

    subgraph FW["  Funky Wallet  "]
        UI["funky-wallet-ui\n:3000  React SPA"]:::frontend
        API["wallet-api-service\n:8080  Spring Boot"]:::backend
        DB[("PostgreSQL\n:5432")]:::db

        subgraph Signing["  MPC Signing  "]
            SC["signing-coordinator\n:9000"]:::signing
            MPC1["mpc-node-1\n:9011"]:::signing
            MPC2["mpc-node-2\n:9012"]:::signing
            MPC3["mpc-node-3\n:9013"]:::signing
        end

        subgraph Adapters["  Chain Adapters  "]
            EVM["evm-chain-adapter\n:9090  web3j"]:::adapter
            SOL["solana-chain-adapter\n:9091  solanaj"]:::adapter
        end
    end

    subgraph Chains["  Blockchains  "]
        Hoodi["Hoodi Testnet\nEVM"]:::chain
        DevNet["Solana Devnet"]:::chain
        Geth["Local Geth\ne2e only"]:::chain
    end

    Browser -- "HTTPS" --> Auth0
    Browser -- "HTTPS" --> UI
    UI -- "Bearer JWT · /api proxy" --> API
    API -- "JPA" --> DB
    API -- "HTTP" --> SC
    API -- "HTTP" --> EVM
    API -- "HTTP" --> SOL
    SC -- "MPC rounds" --> MPC1
    SC -- "MPC rounds" --> MPC2
    SC -- "MPC rounds" --> MPC3
    EVM -- "JSON-RPC" --> Hoodi
    EVM -- "JSON-RPC" --> Geth
    SOL -- "JSON-RPC" --> DevNet
```

---

## 2. Component Detail

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#f0f4ff', 'lineColor': '#555'}}}%%
graph LR
    classDef ui      fill:#27AE60,stroke:#1E8449,color:#fff
    classDef api     fill:#E67E22,stroke:#CA6F1E,color:#fff
    classDef signing fill:#F39C12,stroke:#D68910,color:#fff
    classDef adapter fill:#1ABC9C,stroke:#17A589,color:#fff

    subgraph UI["  funky-wallet-ui :3000  "]
        AuthGuard["AuthGuard\nAuth0 redirect"]:::ui
        ApiToken["ApiTokenProvider\ninjects Bearer"]:::ui
        Store["Zustand Store\naccounts · activeAccount\nnot in localStorage"]:::ui
        Dashboard["Dashboard\nPortfolio + filters"]:::ui
        Activity["Activity\nall user txs"]:::ui
        Send["Send\nform + mnemonic"]:::ui
    end

    subgraph API["  wallet-api-service :8080  "]
        JWTFilter["JwtAuthenticationFilter\nextract sub → SecurityContext"]:::api
        AccSvc["AccountService\nuserId-scoped CRUD"]:::api
        TxSvc["TransactionService\nuserId-scoped + paginated"]:::api
        EVMWatch["watchBlocks()\nEVM block iteration"]:::api
        SolWatch["watchSolanaAccounts()\nper-address signatures"]:::api
        ChainClient["ChainAdapterClient\nEVM → :9090\nSolana → :9091"]:::api
        SignClient["SigningCoordinatorClient"]:::api
    end

    subgraph SC["  signing-coordinator :9000  "]
        Vault["MnemonicVault\nAES-256-GCM in-memory"]:::signing
        EVMSign["EVM\nBIP-32 secp256k1\nm/44'/60'/0'/0/0"]:::signing
        SolSign["Solana\nSLIP-0010 ed25519\nm/44'/501'/0'/0'"]:::signing
    end

    subgraph Adapters["  Chain Adapters  "]
        Web3j["evm-chain-adapter :9090\nweb3j · Hoodi / Geth"]:::adapter
        Solanaj["solana-chain-adapter :9091\nsolanaj · Devnet"]:::adapter
    end

    AuthGuard --> ApiToken --> Store
    Dashboard & Activity & Send --> Store
    Dashboard & Activity & Send -- "axios + Bearer" --> JWTFilter
    JWTFilter --> AccSvc & TxSvc
    AccSvc & TxSvc --> ChainClient & SignClient
    EVMWatch & SolWatch --> ChainClient
    ChainClient --> Web3j & Solanaj
    SignClient --> Vault --> EVMSign & SolSign
```

---

## 3. Database Schema

```mermaid
%%{init: {'theme': 'forest'}}%%
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
        TEXT chain_details "JSON: Solana nonceAccount+nonceAuthority"
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
        VARCHAR network UK
        BIGINT lastProcessedBlock
        VARCHAR lastProcessedBlockHash
        TIMESTAMP updatedAt
    }

    solana_sync_state {
        VARCHAR address PK
        VARCHAR lastSignature
        TIMESTAMP updatedAt
    }
```

### `chain_details` JSON by network

| Network | Value |
|---------|-------|
| EVM | `null` |
| Solana | `{"nonceAccount":"<base58>","nonceAuthority":"<base58>"}` |
| Bitcoin *(future)* | `{"xpub":"...","addressType":"p2wpkh"}` |

---

## 4. Authentication Flow

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'actorBkg': '#9B59B6', 'actorTextColor': '#fff', 'actorBorder': '#7D3C98', 'signalColor': '#555', 'noteBkgColor': '#f8f0ff', 'noteBorderColor': '#9B59B6'}}}%%
sequenceDiagram
    actor User
    participant UI as funky-wallet-ui
    participant Auth0 as Auth0
    participant Istio as Istio Sidecar
    participant API as wallet-api-service

    rect rgb(230, 210, 255)
        Note over User,Auth0: Login
        User->>UI: navigate to app
        UI->>Auth0: redirect to Universal Login
        Auth0-->>User: login page
        User->>Auth0: credentials
        Auth0-->>UI: JWT (sub = userId)
    end

    rect rgb(210, 240, 255)
        Note over UI,API: Authenticated request
        User->>UI: view accounts
        UI->>Istio: GET /api/v1/accounts Bearer JWT
        Note over Istio: prod: validates JWT signature\nRequestAuthentication + DENY-all
        Istio->>API: forward (sig already verified)
        API->>API: JwtAuthenticationFilter\ndecode payload → extract sub
        API-->>UI: Account[] filtered by userId
    end
```

---

## 5. EVM Account Creation

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'actorBkg': '#E67E22', 'actorTextColor': '#fff', 'actorBorder': '#CA6F1E', 'noteBkgColor': '#fff8ee', 'noteBorderColor': '#E67E22'}}}%%
sequenceDiagram
    participant UI as funky-wallet-ui
    participant API as wallet-api-service
    participant SC as signing-coordinator
    participant DB as PostgreSQL

    rect rgb(255, 235, 200)
        Note over UI,SC: Key derivation
        UI->>API: POST /api/v1/accounts\n{network, chainId, chainName, networkType}
        API->>SC: POST /mnemonic/generate
        SC-->>API: {mnemonic}
        API->>SC: POST /keypair/derive\n{mnemonic, network:"ETHEREUM"}
        Note over SC: BIP-39 → BIP-32\nm/44'/60'/0'/0/0\nsecp256k1 → 0x address
        SC->>SC: vault.store(address, mnemonic)
        SC-->>API: {address, publicKey}
    end

    rect rgb(255, 220, 180)
        Note over API,DB: Persist
        API->>DB: INSERT accounts\nchain_details = null
        API-->>UI: {account, mnemonic}
        UI->>UI: show mnemonic once\nclear from memory
    end
```

---

## 6. Solana Account Creation

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'actorBkg': '#1ABC9C', 'actorTextColor': '#fff', 'actorBorder': '#17A589', 'noteBkgColor': '#edfaf7', 'noteBorderColor': '#1ABC9C'}}}%%
sequenceDiagram
    participant UI as funky-wallet-ui
    participant API as wallet-api-service
    participant SC as signing-coordinator
    participant SOL as solana-chain-adapter
    participant DB as PostgreSQL

    rect rgb(200, 245, 235)
        Note over UI,SC: Key derivation (ed25519)
        UI->>API: POST /api/v1/accounts\n{network:SOLANA, chainId:0, ...}
        API->>SC: POST /mnemonic/generate
        SC-->>API: {mnemonic}
        API->>SC: POST /keypair/derive\n{mnemonic, network:"SOLANA"}
        Note over SC: BIP-39 → SLIP-0010\nm/44'/501'/0'/0'\ned25519 → base58 address
        SC-->>API: {address, publicKey}
    end

    rect rgb(170, 235, 220)
        Note over API,DB: Nonce account + persist
        API->>SOL: POST /account/setup\n{walletAddress}
        Note over SOL: SHA-256("solana-nonce:"+addr)\n→ nonce account address\n(prod: create on-chain)
        SOL-->>API: {nonceAccount}
        API->>DB: INSERT accounts\nchain_details={"nonceAccount":"...","nonceAuthority":"..."}
        API-->>UI: {account, mnemonic}
    end
```

---

## 7. Send Transaction (EVM)

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'actorBkg': '#3498DB', 'actorTextColor': '#fff', 'actorBorder': '#2980B9', 'noteBkgColor': '#eef6ff', 'noteBorderColor': '#3498DB'}}}%%
sequenceDiagram
    participant UI as funky-wallet-ui
    participant API as wallet-api-service
    participant EVM as evm-chain-adapter
    participant SC as signing-coordinator
    participant Chain as Hoodi / Geth

    rect rgb(200, 230, 255)
        Note over API,Chain: Build unsigned tx
        UI->>API: POST /api/v1/transactions\n{fromAddress, toAddress, amount, network}
        API->>API: verify ownership (userId check)
        API->>EVM: POST /tx/build {from, to, amount}
        EVM->>Chain: eth_getTransactionCount · eth_gasPrice
        Chain-->>EVM: nonce, gasPrice
        EVM-->>API: {unsignedTx: "from|0xRLP"}
    end

    rect rgb(170, 210, 255)
        Note over API,Chain: Sign and broadcast
        API->>SC: POST /transaction/sign\n{accountAddress, unsignedTx, chainId}
        Note over SC: vault.decrypt → mnemonic\nBIP-32 credentials\nEIP-155 sign
        SC-->>API: {signedTx: "0x..."}
        API->>EVM: POST /tx/broadcast {signedTx}
        EVM->>Chain: eth_sendRawTransaction
        Chain-->>EVM: txHash
        EVM-->>API: {txHash}
    end

    rect rgb(140, 195, 255)
        Note over API: Persist + async confirm
        API->>API: save PENDING
        API-->>UI: {tx, hash}
        Note over API: async (3s): set CONFIRMED
    end
```

---

## 8. Send Transaction (Solana)

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'actorBkg': '#8E44AD', 'actorTextColor': '#fff', 'actorBorder': '#6C3483', 'noteBkgColor': '#f8eeff', 'noteBorderColor': '#8E44AD'}}}%%
sequenceDiagram
    participant UI as funky-wallet-ui
    participant API as wallet-api-service
    participant SOL as solana-chain-adapter
    participant SC as signing-coordinator
    participant Chain as Solana Devnet

    rect rgb(235, 210, 255)
        Note over API,Chain: Fetch blockhash
        UI->>API: POST /api/v1/transactions\n{fromAddress, toAddress, amount, network:SOLANA}
        API->>SOL: POST /tx/build {from, to, amount}
        SOL->>Chain: getLatestBlockhash
        Chain-->>SOL: blockhash
        Note over SOL: returns params string\n"from|to|lamports|blockhash"\n(no crypto in adapter)
        SOL-->>API: {unsignedTx: "from|to|lamports|blockhash"}
    end

    rect rgb(215, 180, 255)
        Note over API,Chain: Build · sign · broadcast
        API->>SC: POST /transaction/sign\n{accountAddress, unsignedTx, network:SOLANA}
        Note over SC: SLIP-0010 → ed25519 key\nsolanaj: build Transaction\nSystemProgram.transfer\nsign → base64 wire format
        SC-->>API: {signedTx: "<base64>"}
        API->>SOL: POST /tx/broadcast {signedTx}
        SOL->>Chain: sendRawTransaction (base64)
        Chain-->>SOL: signature
        SOL-->>API: {txHash}
        API-->>UI: {tx:PENDING, hash}
    end
```

---

## 9. EVM Incoming Transaction Detection

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'actorBkg': '#27AE60', 'actorTextColor': '#fff', 'actorBorder': '#1E8449', 'noteBkgColor': '#eefaf3', 'noteBorderColor': '#27AE60'}}}%%
sequenceDiagram
    participant Sched as @Scheduled 15s
    participant BW as BlockWatcherService
    participant EVM as evm-chain-adapter
    participant Chain as Hoodi / Geth
    participant DB as PostgreSQL

    rect rgb(200, 245, 220)
        Note over BW,Chain: Discover new blocks
        Sched->>BW: watchBlocks()
        BW->>DB: SELECT account addresses (all EVM)
        BW->>EVM: GET /block/latest
        EVM->>Chain: eth_getBlockByNumber(LATEST)
        Chain-->>EVM: {blockNumber, blockHash}
        EVM-->>BW: {blockNumber, blockHash}
        BW->>DB: SELECT block_sync_state WHERE network='ETHEREUM'
    end

    rect rgb(170, 235, 200)
        Note over BW,DB: Process each new block
        loop for each new block
            BW->>EVM: GET /block/{n}/transactions
            EVM->>Chain: eth_getBlockByNumber(n, fullTxs)
            Chain-->>EVM: [{hash, from, to, value}]
            EVM-->>BW: [TxInfo]
            loop toAddress in watchedAddresses
                BW->>DB: INSERT tx (RECEIVED, blockHash)
            end
            BW->>DB: UPDATE block_sync_state lastProcessedBlock=n
        end
    end
```

---

## 10. Solana Incoming Transaction Detection

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'actorBkg': '#E74C3C', 'actorTextColor': '#fff', 'actorBorder': '#CB4335', 'noteBkgColor': '#fff0ee', 'noteBorderColor': '#E74C3C'}}}%%
sequenceDiagram
    participant Sched as @Scheduled 15s
    participant BW as BlockWatcherService
    participant SOL as solana-chain-adapter
    participant Chain as Solana Devnet
    participant DB as PostgreSQL

    rect rgb(255, 210, 205)
        Note over BW,DB: Load per-address state
        Sched->>BW: watchSolanaAccounts()
        BW->>DB: SELECT addresses WHERE networkType='SOLANA'
    end

    rect rgb(255, 185, 178)
        Note over BW,DB: Poll each address
        loop for each Solana address
            BW->>DB: SELECT solana_sync_state (lastSignature)
            BW->>SOL: GET /account/{addr}/new-transactions?since={lastSig}
            SOL->>Chain: getSignaturesForAddress(addr, 50, FINALIZED)
            Chain-->>SOL: [signatures newest-first]
            loop each sig until lastSig
                SOL->>Chain: getTransaction(sig)
                Chain-->>SOL: {accountKeys, preBalances, postBalances}
                Note over SOL: delta=post-pre for our address\nskip if delta ≤ 0
            end
            SOL-->>BW: [IncomingTx{sig, from, to, amount, blockTime}]
            loop each IncomingTx
                BW->>DB: INSERT tx (RECEIVED, confirmedAt=blockTime)
            end
            BW->>DB: UPDATE solana_sync_state lastSignature=newest
        end
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
| Bitcoin *(future)* | Poll UTXOs or address subscriptions | TBD |

### Direction-aware deduplication
Each on-chain transaction can create **two DB records** — one SENT, one RECEIVED — when both ends belong to the same user (self-send). Dedup uses status-aware queries:

- `existsByHashAndFromAddressAndStatus(hash, from, CONFIRMED)` — prevents duplicate SENT records
- `existsByHashAndToAddressAndStatus(hash, to, RECEIVED)` — prevents duplicate RECEIVED records

Without the `status` predicate, the CONFIRMED record's `toAddress` would match the RECEIVED dedup check and block it. The `hash` column has **no unique constraint** by design.

### Transaction direction in UI
Direction is derived from `tx.status`, not address matching:
- `status === 'RECEIVED'` → "↓ Received"
- `status === 'CONFIRMED'` or `'PENDING'` → "↑ Sent"

Address matching fails for self-sends (sender is also a user account), making both records show as "Sent".

---

## 12. Mono-Repo Structure

All services live in a single git repository (`github.com/dipans/funky-wallet`):

```
funky-wallet/
├── .github/workflows/       ← CI/CD (GitHub Actions)
├── funky-wallet-ui/         ← React SPA (Pixel)
├── wallet-api-service/      ← Spring Boot API (Forge)
├── evm-chain-adapter/       ← EVM JSON-RPC adapter (Forge)
├── solana-chain-adapter/    ← Solana JSON-RPC adapter (Forge)
├── mock-services/           ← MPC signing + mock chain (Phantom)
├── funky-wallet-e2e/        ← Playwright e2e tests (Scout)
├── funky-infra/             ← Kubernetes + Helm + Istio (Grid)
└── geth-dev/                ← Local Geth node (e2e only)
```

Previous separate repos (`dipans/wallet-api-service`, `dipans/funky-wallet-ui`, etc.) are archived on GitHub — history preserved there.

---

## 13. CI/CD Pipeline

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#f0f4ff', 'lineColor': '#555'}}}%%
graph LR
    classDef trigger  fill:#4A90D9,stroke:#2C6FAC,color:#fff,font-weight:bold
    classDef ci       fill:#27AE60,stroke:#1E8449,color:#fff,font-weight:bold
    classDef build    fill:#E67E22,stroke:#CA6F1E,color:#fff,font-weight:bold
    classDef deploy   fill:#9B59B6,stroke:#7D3C98,color:#fff,font-weight:bold
    classDef registry fill:#E74C3C,stroke:#CB4335,color:#fff,font-weight:bold
    classDef e2e      fill:#F39C12,stroke:#D68910,color:#fff,font-weight:bold

    PR["Pull Request\nor push to master"]:::trigger
    NightlyManual["Nightly 2am UTC\nor workflow_dispatch"]:::trigger

    subgraph CICD["  GitHub Actions  "]
        Filter["dorny/paths-filter\ndetect changed services"]:::ci
        JavaCI["Java CI\ncompile + mvn test"]:::ci
        UICI["UI CI\nnpm build + vitest"]:::ci
        HelmCI["Helm CI\nlint + template dry-run"]:::ci
        BuildPush["build-push.yml\nDocker build per service"]:::build
        E2EBuild["E2E: docker compose\nup --build (all images)"]:::e2e
        E2ETest["Playwright tests\nChromium headless"]:::e2e
    end

    GHCR["ghcr.io/dipans/\n:latest + :sha"]:::registry
    K8s["Docker Desktop k8s\nfunky-wallet namespace"]:::deploy

    PR --> Filter
    Filter -->|wallet-api-service/**| JavaCI
    Filter -->|funky-wallet-ui/**| UICI
    Filter -->|funky-infra/**| HelmCI
    PR -->|push to master| BuildPush
    BuildPush --> GHCR
    NightlyManual --> E2EBuild
    E2EBuild --> E2ETest
    GHCR -.->|helm upgrade| K8s
```

### Workflow summary

| Workflow | Trigger | Jobs |
|----------|---------|------|
| `ci.yml` | Every PR + push to master | compile + test for each changed service (path-filtered) |
| `build-push.yml` | Push to master | build Docker image + push to GHCR for each changed service |
| `e2e.yml` | Manual + nightly 2am UTC | `docker compose up --build` → Playwright Chromium tests |

### Image registry
All images at `ghcr.io/dipans/<service>` — tagged `:latest` and `:<git-sha>`.
`GITHUB_TOKEN` provides GHCR write access — no additional secrets required.

### Local k8s deploy
Docker Desktop uses the **containerd image store** (`containerd-snapshotter: true`), so locally-built images are available to k8s without a registry push:
```bash
docker build -t ghcr.io/dipans/wallet-api-service:latest wallet-api-service/
# image immediately available in k8s — no push needed
```
