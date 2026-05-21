Build all service images, install Istio (if not present), and deploy the full FunkyWallet stack to Docker Desktop Kubernetes.

Usage: `/funkydeploy` [--skip-build] [--skip-istio]

## Steps

### 1. Check prerequisites
```bash
kubectl config use-context docker-desktop
kubectl get nodes
docker info | grep "Storage Driver"   # must show containerd/overlayfs
```
Fail fast if Docker Desktop k8s is not ready or containerd image store is not active.

### 2. Build all Docker images (skip with --skip-build)

Build each service image with the `ghcr.io/dipans/` prefix so they are immediately available to k8s:

```bash
FUNKY=/Users/dipan/MyResources/Projects/funky-wallet

docker build -t ghcr.io/dipans/wallet-api-service:latest      $FUNKY/wallet-api-service/
docker build -t ghcr.io/dipans/funky-wallet-ui:latest         $FUNKY/funky-wallet-ui/
docker build -t ghcr.io/dipans/evm-chain-adapter:latest       $FUNKY/evm-chain-adapter/
docker build -t ghcr.io/dipans/solana-chain-adapter:latest    $FUNKY/solana-chain-adapter/
docker build -t ghcr.io/dipans/mock-signing-coordinator:latest $FUNKY/mock-services/mock-signing-coordinator/
docker build -t ghcr.io/dipans/geth:latest                    $FUNKY/geth-dev/
```

Report each image size after build.

### 3. Apply namespace
```bash
kubectl apply -f /Users/dipan/MyResources/Projects/funky-infra/k8s/base/namespace.yaml
```

### 4. Install Istio (skip with --skip-istio)

Check if Istio is installed:
```bash
kubectl get pods -n istio-system 2>/dev/null | grep -q "istiod" && echo "already installed" || istioctl install --set profile=demo -y
```

Apply Funky Wallet Istio policies only after Istio is installed:
```bash
kubectl apply -f /Users/dipan/MyResources/Projects/funky-infra/istio/peer-auth.yaml
# Note: request-auth.yaml and authz-policy.yaml require real Auth0 credentials — skip for local dev
```

### 5. Helm upgrade / install
```bash
INFRA=/Users/dipan/MyResources/Projects/funky-infra/helm/funky-wallet

helm upgrade --install funky-wallet $INFRA \
  -f $INFRA/values/postgres.yaml \
  -f $INFRA/values/wallet-api-service.yaml \
  -f $INFRA/values/funky-wallet-ui.yaml \
  -f $INFRA/values/signing-coordinator.yaml \
  -f $INFRA/values/evm-chain-adapter.yaml \
  -f $INFRA/values/solana-chain-adapter.yaml \
  -f $INFRA/values/geth.yaml \
  -f $INFRA/values-dev.yaml \
  --set secrets.postgresUsername=funky \
  --set secrets.postgresPassword=funky \
  --namespace funky-wallet \
  --create-namespace
```

### 6. Wait for all pods to be ready
```bash
kubectl rollout status deployment/wallet-api-service   -n funky-wallet --timeout=120s
kubectl rollout status deployment/signing-coordinator  -n funky-wallet --timeout=120s
kubectl rollout status deployment/evm-chain-adapter    -n funky-wallet --timeout=120s
kubectl rollout status deployment/solana-chain-adapter -n funky-wallet --timeout=120s
kubectl rollout status deployment/funky-wallet-ui      -n funky-wallet --timeout=60s
kubectl rollout status deployment/geth-node            -n funky-wallet --timeout=60s
```

### 7. Smoke test
```bash
# Port-forward the API and hit health
kubectl port-forward svc/wallet-api-service 8081:8080 -n funky-wallet &
sleep 3
curl -sf http://localhost:8081/api/v1/health | jq .
kill %1
```

### 8. Report final status table

| Pod | Ready | Restarts | Image |
|-----|-------|----------|-------|
| wallet-api-service | | | ghcr.io/dipans/wallet-api-service:latest |
| funky-wallet-ui | | | ghcr.io/dipans/funky-wallet-ui:latest |
| postgres | | | postgres:16-alpine |
| signing-coordinator | | | ghcr.io/dipans/mock-signing-coordinator:latest |
| evm-chain-adapter | | | ghcr.io/dipans/evm-chain-adapter:latest |
| solana-chain-adapter | | | ghcr.io/dipans/solana-chain-adapter:latest |
| geth-node | | | ghcr.io/dipans/geth:latest |

On failure: `kubectl describe pod <pod> -n funky-wallet` and report Events section.

Access via port-forward:
```
kubectl port-forward svc/wallet-api-service 8081:8080 -n funky-wallet
kubectl port-forward svc/funky-wallet-ui   3001:3000  -n funky-wallet
```
