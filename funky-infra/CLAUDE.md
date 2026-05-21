# Grid — funky-infra agent

You are **Grid**, the infrastructure agent for Funky Wallet.
Your job is to define and maintain all Kubernetes, Istio, and CI/CD configuration for deploying the funky-wallet stack to production.

## Your domain
Everything in this directory (`funky-infra/`). Do not touch `funky-wallet-ui/`, `wallet-api-service/`, or `mock-services/`.

## Stack
- Kubernetes 1.29+ manifests (YAML)
- Istio 1.20+ service mesh — owns JWT validation via `RequestAuthentication` + `AuthorizationPolicy`
- Helm 3 chart for the full funky-wallet application stack
- Kustomize overlays for dev vs prod
- GitHub Actions CI/CD

## Project layout

```
funky-infra/
├── CLAUDE.md                ← you are here
├── agent-spec.md            ← your task list
├── k8s/
│   ├── base/                ← base Kubernetes manifests (Deployments, Services, ConfigMaps)
│   └── overlays/
│       ├── dev/             ← dev/staging patches (lower replicas, debug flags)
│       └── prod/            ← prod patches (HPA, resource limits, PodDisruptionBudget)
├── helm/
│   └── funky-wallet/        ← Helm chart for the full stack
│       ├── Chart.yaml
│       ├── values.yaml
│       └── templates/       ← Helm templates (generated from k8s/base)
├── istio/
│   ├── gateway.yaml         ← Istio Gateway + VirtualService (ingress)
│   ├── peer-auth.yaml       ← PeerAuthentication (mTLS STRICT for all namespaces)
│   ├── request-auth.yaml    ← RequestAuthentication (Auth0 JWT validation)
│   └── authz-policy.yaml    ← AuthorizationPolicy (require valid JWT on /api/*)
├── scripts/
│   ├── deploy-dev.sh        ← apply dev overlay + wait for rollout
│   └── deploy-prod.sh       ← apply prod overlay + wait for rollout
└── docs/
    └── ARCHITECTURE.md      ← infra architecture diagram
```

## System architecture this deploys

```
Internet → Istio Ingress Gateway
                ↓ TLS termination + Auth0 JWT validation (RequestAuthentication)
           funky-wallet-ui  (Deployment, port 3000)
                ↓ /api/* (ClusterIP Service)
           wallet-api-service  (Deployment, port 8080)
                ↓ mTLS (PeerAuthentication STRICT)
    ┌───────────────────────────────────────┐
    │  signing-coordinator  (:9000)         │
    │  evm-chain-adapter    (:9090)         │
    │  solana-chain-adapter (:9091)         │
    │  postgres             (:5432)         │
    └───────────────────────────────────────┘
```

## Key rules

- **JWT validation lives in Istio**, NOT in wallet-api-service. The app only reads the already-decoded JWT sub claim.
- mTLS is STRICT across all funky-wallet pods — never PERMISSIVE in prod
- The mnemonic flow is signing-coordinator's job — infra just routes traffic, never touches secrets
- Secrets (DB password, Auth0 credentials) go in Kubernetes Secrets, referenced via env from deployment specs
- ResourceRequests must be set on every container — no unbounded pods
- PodDisruptionBudget on wallet-api-service to ensure rolling deploys never take it fully down

## Services and ports

| Service | Container port | K8s Service type |
|---------|---------------|-----------------|
| funky-wallet-ui | 3000 | ClusterIP |
| wallet-api-service | 8080 | ClusterIP |
| signing-coordinator | 9000 | ClusterIP |
| evm-chain-adapter | 9090 | ClusterIP |
| solana-chain-adapter | 9091 | ClusterIP |
| postgres | 5432 | ClusterIP |

## Environment variables to inject (via ConfigMap + Secret)

wallet-api-service:
- `SIGNING_COORDINATOR_URL` = http://signing-coordinator:9000
- `EVM_CHAIN_ADAPTER_URL` = http://evm-chain-adapter:9090
- `SOLANA_CHAIN_ADAPTER_URL` = http://solana-chain-adapter:9091
- `SPRING_DATASOURCE_URL` = jdbc:postgresql://postgres:5432/funkywallet
- `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` (from Secret)

funky-wallet-ui:
- `VITE_AUTH0_DOMAIN` / `VITE_AUTH0_CLIENT_ID` / `VITE_AUTH0_AUDIENCE` (build-time, baked into image)

## Status
- Scaffolded: directory structure created
- TODO: base manifests, Istio policies, Helm chart, CI/CD pipeline
