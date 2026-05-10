FROM ethereum/client-go:v1.13.14

COPY genesis.json /genesis.json

# Initialise the genesis block then start geth
# Clique PoA: fast 2s blocks, no mining CPU cost, deterministic
ENTRYPOINT ["/bin/sh", "-c", "\
  if [ ! -d /root/.ethereum/geth ]; then \
    geth init --datadir /root/.ethereum /genesis.json; \
  fi && \
  geth \
    --datadir /root/.ethereum \
    --networkid 1337 \
    --http \
    --http.addr 0.0.0.0 \
    --http.port 8545 \
    --http.api eth,net,web3,personal,debug,txpool \
    --http.corsdomain '*' \
    --http.vhosts '*' \
    --ws \
    --ws.addr 0.0.0.0 \
    --ws.port 8546 \
    --ws.api eth,net,web3 \
    --ws.origins '*' \
    --allow-insecure-unlock \
    --unlock 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 \
    --password /dev/null \
    --mine \
    --miner.etherbase 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 \
    --nodiscover \
    --verbosity 3 \
"]
