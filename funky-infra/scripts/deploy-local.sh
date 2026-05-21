#!/usr/bin/env bash
# Deploy funky-wallet to Docker Desktop Kubernetes using local images.
#
# Prerequisites:
#   1. Docker Desktop running with Kubernetes enabled
#   2. daemon.json includes "insecure-registries": ["host.docker.internal:5001"]
#      (already set — requires a one-time Docker Desktop restart to take effect)
#   3. Local images built (from docker-compose in wallet-api-service/ etc.)
#
# Run: bash scripts/deploy-local.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART="$SCRIPT_DIR/../helm/funky-wallet"
VALUES="$CHART/values"
REGISTRY="localhost:5001"

GREEN='\033[0;32m'; CYAN='\033[0;36m'; YELLOW='\033[1;33m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓${NC} $*"; }
info() { echo -e "${CYAN}→${NC} $*"; }
warn() { echo -e "${YELLOW}⚠${NC} $*"; }

# ── 1. Ensure local registry is running ─────────────────────────────────────
if ! docker ps --filter name=local-registry --filter status=running -q | grep -q .; then
  info "Starting local registry on port 5001..."
  docker rm -f local-registry 2>/dev/null || true
  docker run -d --name local-registry --restart=always -p 5001:5000 registry:2
fi
ok "Local registry running at localhost:5001"

# ── 2. Build & push images to local registry ─────────────────────────────────
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FUNKY="$ROOT/funky-wallet"   # adjust if the funky-wallet dir is elsewhere

info "Tagging and pushing images to local registry..."

push_image() {
  local src=$1 name=$2
  docker tag "$src" "$REGISTRY/dipans/$name:latest"
  docker push "$REGISTRY/dipans/$name:latest"
  ok "$name"
}

push_image "funky-wallet-wallet-api-service:latest"     "wallet-api-service"
push_image "funky-wallet-funky-wallet-ui:latest"        "funky-wallet-ui"
push_image "funky-wallet-evm-chain-adapter:latest"      "evm-chain-adapter"
push_image "wallet-api-service-solana-chain-adapter:latest" "solana-chain-adapter"
push_image "funky-wallet-mock-signing-coordinator:latest"   "mock-signing-coordinator"

# ── 3. Apply namespace ────────────────────────────────────────────────────────
kubectl config use-context docker-desktop
kubectl apply -f "$SCRIPT_DIR/../k8s/base/namespace.yaml"

# ── 4. Helm upgrade ───────────────────────────────────────────────────────────
info "Running helm upgrade (local)..."
helm upgrade --install funky-wallet "$CHART" \
  -f "$VALUES/postgres.yaml" \
  -f "$VALUES/wallet-api-service.yaml" \
  -f "$VALUES/funky-wallet-ui.yaml" \
  -f "$VALUES/signing-coordinator.yaml" \
  -f "$VALUES/evm-chain-adapter.yaml" \
  -f "$VALUES/solana-chain-adapter.yaml" \
  -f "$CHART/values-local.yaml" \
  -f "$CHART/values-dev.yaml" \
  --set secrets.postgresUsername=funky \
  --set secrets.postgresPassword=funky \
  --namespace funky-wallet \
  --create-namespace \
  --wait \
  --timeout 5m

ok "Helm release deployed"

# ── 5. Status ─────────────────────────────────────────────────────────────────
kubectl get pods -n funky-wallet
echo ""
warn "Services are ClusterIP-only. To access them locally:"
echo "  kubectl port-forward svc/wallet-api-service 8081:8080 -n funky-wallet &"
echo "  kubectl port-forward svc/funky-wallet-ui   3001:3000  -n funky-wallet &"
echo "  curl http://localhost:8081/api/v1/health"
