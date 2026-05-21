Deploy the full FunkyWallet stack to Docker Desktop Kubernetes (dev) or a remote cluster (prod).

Usage: `/funkyinfra-up` — defaults to dev on Docker Desktop

## Steps

### 1. Run the deploy script
```bash
bash /Users/dipan/MyResources/Projects/funky-infra/scripts/deploy-dev.sh
```

For prod (requires env vars POSTGRES_PASSWORD, AUTH0_DOMAIN, AUTH0_CLIENT_ID, KUBE_CONTEXT):
```bash
POSTGRES_PASSWORD=... AUTH0_DOMAIN=... AUTH0_CLIENT_ID=... KUBE_CONTEXT=prod-cluster \
  bash /Users/dipan/MyResources/Projects/funky-infra/scripts/deploy-prod.sh
```

### What it does
- Switches kubectl context to `docker-desktop`
- Applies `funky-wallet` namespace (with Istio injection label)
- Merges per-service values files + env overlay, then runs `helm upgrade --install`
- Waits for all Deployments to become ready

### 2. Verify rollout
```bash
kubectl get pods -n funky-wallet
kubectl rollout status deployment/wallet-api-service -n funky-wallet
```

### 3. Smoke test the API
```bash
kubectl run smoke --image=curlimages/curl --rm -it --restart=Never -n funky-wallet \
  -- curl -sf http://wallet-api-service:8080/api/v1/health | jq .
```

### 4. Report status table

| Pod | Ready | Status |
|-----|-------|--------|
| wallet-api-service | kubectl output | |
| funky-wallet-ui | kubectl output | |
| postgres | kubectl output | |
| signing-coordinator | kubectl output | |
| evm-chain-adapter | kubectl output | |
| solana-chain-adapter | kubectl output | |

On failure: `kubectl describe pod <failing-pod> -n funky-wallet` and report the Events section with a fix suggestion.

### Notes on Docker Desktop
- Enable Kubernetes in Docker Desktop → Preferences → Kubernetes → Enable Kubernetes
- The context name is `docker-desktop`
- Services are ClusterIP only — use `kubectl port-forward` to reach them locally:
  ```bash
  kubectl port-forward svc/wallet-api-service 8080:8080 -n funky-wallet
  kubectl port-forward svc/funky-wallet-ui 3000:3000 -n funky-wallet
  ```
