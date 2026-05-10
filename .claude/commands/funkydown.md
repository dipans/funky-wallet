Tear down the FunkyWallet development ecosystem cleanly.

## Steps

1. Run the stop script:
```bash
cd /Users/dipan/MyResources/Projects/funky-wallet
bash scripts/stop-dev.sh
```

2. Verify all processes are stopped:
```bash
# Check no funkywallet processes remain
lsof -i :3000 -i :8080 -i :9000 -i :9090 -i :5432 2>/dev/null | grep LISTEN || echo "All ports free"

# Check Docker containers stopped
docker ps --filter "name=funkywallet" --format "table {{.Names}}\t{{.Status}}"
```

3. Report what was stopped and confirm all ports are free.

If the script fails (e.g. a PID file is stale or a port is still bound), kill the processes manually:
```bash
lsof -ti :8080 | xargs kill -9 2>/dev/null || true
lsof -ti :3000 | xargs kill -9 2>/dev/null || true
docker compose -f mock-services/docker-compose.mock.yml down --remove-orphans
docker compose -f wallet-api-service/docker-compose.dev.yml down
```
