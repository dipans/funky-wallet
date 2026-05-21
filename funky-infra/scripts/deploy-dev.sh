#!/usr/bin/env bash
# Deploy the full funky-wallet stack to the Docker Desktop Kubernetes cluster (dev).
# Requires: kubectl (Docker Desktop context), helm 3
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART="$SCRIPT_DIR/../helm/funky-wallet"
VALUES="$CHART/values"

GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓${NC} $*"; }
info() { echo -e "${CYAN}→${NC} $*"; }

# Ensure we're on Docker Desktop context
info "Switching kubectl context to docker-desktop..."
kubectl config use-context docker-desktop

# Apply namespace + Istio policies (if Istio is installed)
info "Applying namespace..."
kubectl apply -f "$SCRIPT_DIR/../k8s/base/namespace.yaml"

if kubectl get crd gateways.networking.istio.io >/dev/null 2>&1; then
  info "Applying Istio policies..."
  kubectl apply -f "$SCRIPT_DIR/../istio/"
else
  echo "  (Istio not installed — skipping Istio policies)"
fi

# Helm upgrade/install
info "Running helm upgrade (dev)..."
helm upgrade --install funky-wallet "$CHART" \
  -f "$VALUES/postgres.yaml" \
  -f "$VALUES/wallet-api-service.yaml" \
  -f "$VALUES/funky-wallet-ui.yaml" \
  -f "$VALUES/signing-coordinator.yaml" \
  -f "$VALUES/evm-chain-adapter.yaml" \
  -f "$VALUES/solana-chain-adapter.yaml" \
  -f "$CHART/values-dev.yaml" \
  --namespace funky-wallet \
  --create-namespace \
  --wait \
  --timeout 5m

ok "Helm release deployed (dev)"

# Rollout status
info "Checking rollout status..."
kubectl rollout status deployment/wallet-api-service -n funky-wallet --timeout=120s
kubectl rollout status deployment/funky-wallet-ui -n funky-wallet --timeout=120s

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  funky-wallet is UP on Docker Desktop k8s${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
kubectl get pods -n funky-wallet
