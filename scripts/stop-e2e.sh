#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓${NC} $*"; }
info() { echo -e "${CYAN}→${NC} $*"; }

info "Stopping e2e stack..."
docker compose -f "$ROOT/docker-compose.e2e.yml" down --remove-orphans 2>/dev/null
ok "e2e stack stopped"
echo ""
