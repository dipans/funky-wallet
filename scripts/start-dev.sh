#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PIDS_DIR="$ROOT/.pids"
LOGS_DIR="$ROOT/.logs"

# ── colours ──────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓${NC} $*"; }
info() { echo -e "${CYAN}→${NC} $*"; }
warn() { echo -e "${YELLOW}⚠${NC} $*"; }
fail() { echo -e "${RED}✗${NC} $*"; exit 1; }

mkdir -p "$PIDS_DIR" "$LOGS_DIR"

# ── prerequisites ─────────────────────────────────────────────────────────────
info "Checking prerequisites..."
command -v docker  >/dev/null 2>&1 || fail "Docker not found"
command -v java    >/dev/null 2>&1 || fail "Java not found"
command -v node    >/dev/null 2>&1 || fail "Node.js not found"
docker info        >/dev/null 2>&1 || fail "Docker daemon not running — start Docker Desktop first"
ok "Prerequisites OK"

# ── helper: wait for HTTP health endpoint ────────────────────────────────────
wait_http() {
  local name=$1 url=$2 retries=${3:-30}
  info "Waiting for $name ($url)..."
  for i in $(seq 1 $retries); do
    if curl -sf "$url" >/dev/null 2>&1; then
      ok "$name is up"
      return 0
    fi
    sleep 2
  done
  fail "$name did not become healthy after $((retries * 2))s"
}

# ── helper: wait for POST endpoint ───────────────────────────────────────────
wait_http_post() {
  local name=$1 url=$2 retries=${3:-30}
  info "Waiting for $name ($url)..."
  for i in $(seq 1 $retries); do
    if curl -sf -X POST "$url" -H 'Content-Type: application/json' -d '{}' >/dev/null 2>&1; then
      ok "$name is up"
      return 0
    fi
    sleep 2
  done
  fail "$name did not become healthy after $((retries * 2))s"
}

# ── 1. Postgres + evm-chain-adapter (Hoodi testnet) ──────────────────────────
info "Starting Postgres and evm-chain-adapter..."
docker compose -f "$ROOT/wallet-api-service/docker-compose.dev.yml" up -d --build --quiet-pull
info "Waiting for Postgres to be healthy..."
for i in $(seq 1 20); do
  if docker exec funkywallet-postgres pg_isready -U funky -d funkywallet_dev >/dev/null 2>&1; then
    ok "Postgres ready"
    break
  fi
  [ "$i" -eq 20 ] && fail "Postgres did not become ready"
  sleep 2
done
wait_http "evm-chain-adapter (Hoodi)" "http://localhost:9090/balance?address=0x0&network=ETHEREUM" 30

# ── 2. Mock signing coordinator ───────────────────────────────────────────────
info "Starting mock-signing-coordinator..."
docker compose -f "$ROOT/mock-services/docker-compose.mock.yml" up -d --build mock-signing-coordinator mock-mpc-node-1 mock-mpc-node-2 mock-mpc-node-3
wait_http_post "mock-signing-coordinator" "http://localhost:9000/mnemonic/generate" 20

# ── 3. wallet-api-service ─────────────────────────────────────────────────────
info "Starting wallet-api-service (Liquibase migrations + test data seed)..."
nohup bash -c "cd '$ROOT/wallet-api-service' && ./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local \
  > '$LOGS_DIR/wallet-api-service.log' 2>&1" &
echo $! > "$PIDS_DIR/wallet-api-service.pid"
wait_http "wallet-api-service" "http://localhost:8080/api/v1/health" 45

# ── 4. funky-wallet-ui ────────────────────────────────────────────────────────
info "Starting funky-wallet-ui dev server..."
nohup bash -c "cd '$ROOT/funky-wallet-ui' && npm run dev \
  > '$LOGS_DIR/funky-wallet-ui.log' 2>&1" &
echo $! > "$PIDS_DIR/funky-wallet-ui.pid"
wait_http "funky-wallet-ui" "http://localhost:3000" 20

# ── 5. Verify test data ───────────────────────────────────────────────────────
info "Verifying test data..."
ACCOUNT=$(curl -sf "http://localhost:8080/api/v1/accounts/0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266" 2>/dev/null || echo "")
if echo "$ACCOUNT" | grep -q "0xf39F"; then
  ok "Test account found in database"
else
  warn "Test account not found — Liquibase seed may not have run yet, check logs/.logs/wallet-api-service.log"
fi

MNEMONIC_RESP=$(curl -sf -X POST http://localhost:9000/mnemonic/generate 2>/dev/null || echo "")
if echo "$MNEMONIC_RESP" | grep -q "mnemonic"; then
  ok "mock-signing-coordinator responding"
fi

# ── 6. Summary ────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  FunkyWallet ecosystem is UP${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "  ${CYAN}Frontend${NC}   http://localhost:3000"
echo -e "  ${CYAN}API${NC}        http://localhost:8080"
echo -e "  ${CYAN}Swagger${NC}    http://localhost:8080/swagger-ui.html"
echo -e "  ${CYAN}Adminer${NC}    http://localhost:8888  (user: funky / pass: funky)"
echo -e "  ${CYAN}Signing${NC}    http://localhost:9000"
echo -e "  ${CYAN}Chain${NC}      http://localhost:9090  (Hoodi testnet)"
echo ""
echo -e "  ${YELLOW}Test account${NC}  0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
echo -e "  ${YELLOW}Test mnemonic${NC} test test test test test test test test test test test junk"
echo ""
echo -e "  Logs: $LOGS_DIR/"
echo -e "  Stop: ./scripts/stop-dev.sh"
echo ""
