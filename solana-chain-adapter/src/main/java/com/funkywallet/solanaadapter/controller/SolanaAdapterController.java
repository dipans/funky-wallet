package com.funkywallet.solanaadapter.controller;

import com.funkywallet.solanaadapter.service.SolanaService;
import com.funkywallet.solanaadapter.service.SolanaWatcherService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SolanaAdapterController {

    private final SolanaService solanaService;
    private final SolanaWatcherService solanaWatcherService;

    // GET /balance?address=&network=
    @GetMapping("/balance")
    public Map<String, Object> getBalance(@RequestParam String address,
                                          @RequestParam(required = false) String network) {
        BigDecimal balance = solanaService.getBalance(address);
        return Map.of("amount", balance.toPlainString(), "symbol", "SOL");
    }

    // POST /tx/build  { from, to, amount, network }
    @PostMapping("/tx/build")
    public Map<String, String> buildTx(@RequestBody BuildTxRequest req) {
        String unsignedTx = solanaService.buildUnsignedTx(req.getFrom(), req.getTo(), req.getAmount());
        return Map.of("unsignedTx", unsignedTx);
    }

    // POST /tx/broadcast  { signedTx, network }
    @PostMapping("/tx/broadcast")
    public Map<String, String> broadcast(@RequestBody BroadcastRequest req) {
        String txHash = solanaService.broadcast(req.getSignedTx());
        return Map.of("txHash", txHash);
    }

    // POST /account/setup  { walletAddress }
    @PostMapping("/account/setup")
    public Map<String, String> setupAccount(@RequestBody SetupRequest req) {
        String nonceAccount = solanaService.setupNonceAccount(req.getWalletAddress());
        return Map.of("nonceAccount", nonceAccount);
    }

    // GET /account/{address}/new-transactions?since={lastSignature}
    // Used by BlockWatcherService to detect both incoming and outgoing SOL transfers.
    @GetMapping("/account/{address}/new-transactions")
    public List<SolanaWatcherService.SolanaTx> getNewTransactions(
            @PathVariable String address,
            @RequestParam(required = false) String since) {
        return solanaWatcherService.getNewTransactions(address, since);
    }

    // GET /health
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "node", solanaService.getNodeInfo());
    }

    @Data
    public static class BuildTxRequest {
        private String from;
        private String to;
        private BigDecimal amount;
        private String network;
    }

    @Data
    public static class BroadcastRequest {
        private String signedTx;
        private String network;
    }

    @Data
    public static class SetupRequest {
        private String walletAddress;
    }
}
