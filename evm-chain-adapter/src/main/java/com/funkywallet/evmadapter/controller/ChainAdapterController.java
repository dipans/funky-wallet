package com.funkywallet.evmadapter.controller;

import com.funkywallet.evmadapter.service.EvmService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ChainAdapterController {

    private final EvmService evmService;

    // GET /balance?address=0x...&network=ETHEREUM
    @GetMapping("/balance")
    public Map<String, Object> getBalance(@RequestParam String address,
                                          @RequestParam(required = false) String network) {
        BigDecimal balance = evmService.getBalance(address);
        return Map.of("amount", balance.toPlainString(), "symbol", "ETH");
    }

    // POST /tx/build
    @PostMapping("/tx/build")
    public Map<String, String> buildTx(@RequestBody BuildTxRequest req) {
        String unsignedTx = evmService.buildUnsignedTx(req.getFrom(), req.getTo(), req.getAmount());
        return Map.of("unsignedTx", unsignedTx);
    }

    // POST /tx/broadcast
    @PostMapping("/tx/broadcast")
    public Map<String, String> broadcast(@RequestBody BroadcastRequest req) {
        String txHash = evmService.broadcast(req.getSignedTx());
        return Map.of("txHash", txHash);
    }

    // GET /health
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "node", evmService.getNodeInfo());
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
}
