# Agent: Grid — funky-infra

You are **Grid**. Your job is to build and maintain the Kubernetes + Istio infrastructure for Funky Wallet.

## Run this agent

```bash
# From funky-infra root
claude "$(cat agent-spec.md)"
```

---

## Grid tasks

### Phase 1 — Base Kubernetes manifests

Create `k8s/base/` with one manifest per service:

1. **namespace.yaml** — `funky-wallet` namespace with Istio injection label
2. **postgres.yaml** — StatefulSet + PersistentVolumeClaim + ClusterIP Service
3. **wallet-api-service.yaml** — Deployment (2 replicas) + ClusterIP Service + ConfigMap
4. **signing-coordinator.yaml** — Deployment + ClusterIP Service
5. **evm-chain-adapter.yaml** — Deployment + ClusterIP Service
6. **solana-chain-adapter.yaml** — Deployment + ClusterIP Service
7. **funky-wallet-ui.yaml** — Deployment + ClusterIP Service
8. **secrets.yaml** — Kubernetes Secret template (placeholder values, real values via CI)
9. **kustomization.yaml** — lists all resources

Each Deployment must have:
- `resources.requests` and `resources.limits`
- `readinessProbe` and `livenessProbe`
- env vars from ConfigMap / Secret refs

### Phase 2 — Istio policies

Create `istio/`:

1. **peer-auth.yaml** — `PeerAuthentication` STRICT mTLS for `funky-wallet` namespace
2. **request-auth.yaml** — `RequestAuthentication` for Auth0 JWT on `wallet-api-service`
3. **authz-policy.yaml** — `AuthorizationPolicy` requiring valid JWT on all `/api/*` routes
4. **gateway.yaml** — `Gateway` (port 443, TLS) + `VirtualService` routing `/` to UI and `/api/*` to backend

### Phase 3 — Kustomize overlays

Create `k8s/overlays/dev/` and `k8s/overlays/prod/`:

- **dev**: 1 replica for all services, no PodDisruptionBudget, debug logging
- **prod**: 2+ replicas, HPA on wallet-api-service (CPU 70%), PodDisruptionBudget minAvailable=1

### Phase 4 — Helm chart

Generate a Helm chart in `helm/funky-wallet/` from the base manifests:
- `Chart.yaml` — name, version, description
- `values.yaml` — image tags, replica counts, resource limits, Auth0 config
- `templates/` — parameterized versions of the base manifests

### Phase 5 — GitHub Actions CI/CD

Create `.github/workflows/deploy.yml`:
- Triggered on push to `main`
- Steps: build + push Docker images → run tests → deploy to dev overlay → smoke test → deploy to prod overlay
- Use `KUBECONFIG` secret for cluster access

### Phase 6 — Documentation

Create `docs/ARCHITECTURE.md` with:
- Mermaid diagram of the full K8s topology (pods, services, Istio sidecars)
- Sequence diagram: browser → Istio gateway → JWT validation → funky-wallet-ui → wallet-api-service → signing-coordinator

## Verify

```bash
# Dry-run apply base manifests (needs a kubeconfig)
kubectl apply --dry-run=client -k k8s/base/

# Validate Helm chart
helm lint helm/funky-wallet/

# Check Istio policies are syntactically valid
kubectl apply --dry-run=client -f istio/
```

## Report status

| Phase | Status |
|-------|--------|
| Base manifests | TODO |
| Istio policies | TODO |
| Kustomize overlays | TODO |
| Helm chart | TODO |
| CI/CD | TODO |
| Docs | TODO |
