#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓${NC} $*"; }
info() { echo -e "${CYAN}→${NC} $*"; }
fail() { echo -e "${RED}✗${NC} $*"; exit 1; }

# ── Prerequisites ─────────────────────────────────────────────────────────────
command -v docker >/dev/null 2>&1 || fail "Docker not found"
docker info >/dev/null 2>&1       || fail "Docker daemon not running"
ok "Docker OK"

# ── Build and start all e2e containers ────────────────────────────────────────
info "Building and starting e2e stack (this may take a few minutes on first run)..."
docker compose -f "$ROOT/docker-compose.e2e.yml" up -d --build 2>&1 | grep -E "Container|Error|Started|Running" || true

# ── Wait for each service ──────────────────────────────────────────────────────
wait_healthy() {
  local name=$1 retries=${2:-30}
  info "Waiting for $name..."
  for i in $(seq 1 $retries); do
    status=$(docker inspect --format='{{.State.Health.Status}}' "$name" 2>/dev/null || echo "missing")
    [ "$status" = "healthy" ] && ok "$name healthy" && return 0
    [ "$i" -eq "$retries" ] && fail "$name did not become healthy (status: $status)" || sleep 3
  done
}

wait_healthy funkywallet-geth       20
wait_healthy funkywallet-postgres-e2e 15
wait_healthy funkywallet-signing    20
wait_healthy funkywallet-evm-adapter 20
wait_healthy funkywallet-api-e2e    30
wait_healthy funkywallet-ui-e2e     10

# ── Verify test data ──────────────────────────────────────────────────────────
info "Verifying test data..."
ACCOUNT=$(curl -sf "http://localhost:8080/api/v1/accounts/0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266" 2>/dev/null || echo "")
if echo "$ACCOUNT" | grep -q "0xf39F"; then
  ok "Test account seeded"
else
  echo -e "${YELLOW}⚠${NC} Test account not found — Liquibase e2e context may still be running"
fi

GETH=$(curl -sf -X POST http://localhost:8545 \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' 2>/dev/null || echo "")
if echo "$GETH" | grep -q "result"; then
  BLOCK=$(echo "$GETH" | python3 -c "import sys,json; d=json.load(sys.stdin); print(int(d['result'],16))" 2>/dev/null || echo "?")
  ok "Geth mining (block $BLOCK)"
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  FunkyWallet e2e stack is UP${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "  ${CYAN}Frontend${NC}   http://localhost:3000"
echo -e "  ${CYAN}API${NC}        http://localhost:8080"
echo -e "  ${CYAN}Signing${NC}    http://localhost:9000"
echo -e "  ${CYAN}Chain${NC}      http://localhost:9090"
echo -e "  ${CYAN}Geth RPC${NC}   http://localhost:8545"
echo ""
echo -e "  ${YELLOW}Test account${NC}  0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
echo -e "  ${YELLOW}Chain ID${NC}      1337 (Local Geth, Clique PoA)"
echo ""
echo -e "  Run tests: cd funky-wallet-e2e && npm test"
echo -e "  Stop:      bash scripts/stop-e2e.sh"
echo ""
