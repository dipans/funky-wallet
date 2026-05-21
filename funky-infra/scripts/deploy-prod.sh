#!/usr/bin/env bash
# Deploy the full funky-wallet stack to the production Kubernetes cluster.
# Secrets MUST be provided via environment variables.
# Requires: kubectl (prod context), helm 3
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART="$SCRIPT_DIR/../helm/funky-wallet"
VALUES="$CHART/values"

: "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD}"
: "${AUTH0_DOMAIN:?Set AUTH0_DOMAIN}"
: "${AUTH0_CLIENT_ID:?Set AUTH0_CLIENT_ID}"
: "${KUBE_CONTEXT:?Set KUBE_CONTEXT to your prod cluster context}"

GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓${NC} $*"; }
info() { echo -e "${CYAN}→${NC} $*"; }

info "Switching kubectl context to $KUBE_CONTEXT..."
kubectl config use-context "$KUBE_CONTEXT"

info "Applying namespace + Istio policies..."
kubectl apply -f "$SCRIPT_DIR/../k8s/base/namespace.yaml"
kubectl apply -f "$SCRIPT_DIR/../istio/"

info "Running helm upgrade (prod)..."
helm upgrade --install funky-wallet "$CHART" \
  -f "$VALUES/postgres.yaml" \
  -f "$VALUES/wallet-api-service.yaml" \
  -f "$VALUES/funky-wallet-ui.yaml" \
  -f "$VALUES/signing-coordinator.yaml" \
  -f "$VALUES/evm-chain-adapter.yaml" \
  -f "$VALUES/solana-chain-adapter.yaml" \
  -f "$CHART/values-prod.yaml" \
  --set secrets.postgresPassword="$POSTGRES_PASSWORD" \
  --set secrets.auth0Domain="$AUTH0_DOMAIN" \
  --set secrets.auth0ClientId="$AUTH0_CLIENT_ID" \
  --namespace funky-wallet \
  --create-namespace \
  --wait \
  --timeout 10m

ok "Helm release deployed (prod)"
kubectl rollout status deployment/wallet-api-service -n funky-wallet --timeout=180s
kubectl rollout status deployment/funky-wallet-ui -n funky-wallet --timeout=180s
kubectl get pods -n funky-wallet
