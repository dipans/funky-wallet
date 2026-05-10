#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PIDS_DIR="$ROOT/.pids"

GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓${NC} $*"; }
info() { echo -e "${CYAN}→${NC} $*"; }

# ── Stop JVM processes ────────────────────────────────────────────────────────
for name in wallet-api-service funky-wallet-ui; do
  PID_FILE="$PIDS_DIR/$name.pid"
  if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
      info "Stopping $name (pid $PID)..."
      kill "$PID" && ok "$name stopped"
    fi
    rm -f "$PID_FILE"
  fi
done

# ── Stop Docker services ──────────────────────────────────────────────────────
info "Stopping mock services..."
docker compose -f "$ROOT/mock-services/docker-compose.mock.yml" down --remove-orphans 2>/dev/null && ok "Mock services stopped"

info "Stopping Postgres..."
docker compose -f "$ROOT/wallet-api-service/docker-compose.dev.yml" down 2>/dev/null && ok "Postgres stopped"

echo ""
echo -e "${GREEN}FunkyWallet ecosystem stopped.${NC}"
echo ""
