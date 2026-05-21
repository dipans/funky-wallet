package com.funkywallet.chainadapter.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Slf4j
@RestController
public class ChainAdapterController {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.failure-rate:0.05}")
    private double failureRate;

    @Value("${app.block-time-ms:500}")
    private long blockTimeMs;

    // -------------------------------------------------------------------------
    // POST /tx/broadcast
    // -------------------------------------------------------------------------

    record BroadcastRequest(String signedTx, String network) {}

    @PostMapping("/tx/broadcast")
    public ResponseEntity<Map<String, String>> broadcastTransaction(@RequestBody BroadcastRequest req)
            throws InterruptedException {

        // Simulate block time
        if (blockTimeMs > 0) {
            Thread.sleep(blockTimeMs);
        }

        // Simulate configurable failure rate
        if (RANDOM.nextDouble() < failureRate) {
            log.warn("Simulated broadcast failure for network={} (failureRate={})", req.network(), failureRate);
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Simulated chain broadcast failure (failureRate=" + failureRate + ")");
        }

        // Generate a random 32-byte tx hash
        byte[] hashBytes = new byte[32];
        RANDOM.nextBytes(hashBytes);
        String txHash = "0x" + bytesToHex(hashBytes);

        log.debug("Broadcast success for network={} txHash={}", req.network(), txHash);
        return ResponseEntity.ok(Map.of("txHash", txHash));
    }

    // -------------------------------------------------------------------------
    // GET /balance?address=&network=
    // -------------------------------------------------------------------------

    @GetMapping("/balance")
    public ResponseEntity<Map<String, String>> getBalance(
            @RequestParam String address,
            @RequestParam String network) {

        // Random balance between 1.0 and 100.0 (2 decimal places)
        double rawBalance = 1.0 + RANDOM.nextDouble() * 99.0;
        String amount = BigDecimal.valueOf(rawBalance)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();

        String symbol = switch (network.toUpperCase()) {
            case "SOLANA"  -> "SOL";
            case "BITCOIN" -> "BTC";
            default        -> "ETH";
        };

        log.debug("Balance query for address={} network={} -> {} {}", address, network, amount, symbol);
        return ResponseEntity.ok(Map.of("amount", amount, "symbol", symbol));
    }

    // -------------------------------------------------------------------------
    // POST /tx/build
    // -------------------------------------------------------------------------

    record BuildRequest(String from, String to, double amount, String network) {}

    @PostMapping("/tx/build")
    public ResponseEntity<Map<String, String>> buildTransaction(@RequestBody BuildRequest req) {
        // Encode a deterministic-looking hex payload from the request fields
        String payload = String.format(
                "from=%s|to=%s|amount=%.8f|network=%s|nonce=%d",
                req.from(), req.to(), req.amount(), req.network(), RANDOM.nextInt(1_000_000)
        );
        String unsignedTx = bytesToHex(payload.getBytes(StandardCharsets.UTF_8));

        log.debug("Built unsigned tx for network={} from={} to={}", req.network(), req.from(), req.to());
        return ResponseEntity.ok(Map.of("unsignedTx", unsignedTx));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
