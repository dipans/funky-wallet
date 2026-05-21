package com.funkywallet.mpcnode.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@RestController
public class MpcNodeController {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** In-memory share store: nodeId -> share value */
    private final ConcurrentHashMap<String, String> shareStore = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // POST /share/store
    // -------------------------------------------------------------------------

    record StoreRequest(String nodeId, String share) {}

    @PostMapping("/share/store")
    public ResponseEntity<Map<String, Boolean>> storeShare(@RequestBody StoreRequest req) {
        if (req.nodeId() == null || req.nodeId().isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "nodeId must not be blank");
        }
        shareStore.put(req.nodeId(), req.share());
        log.debug("Stored share for nodeId={}", req.nodeId());
        return ResponseEntity.ok(Map.of("stored", true));
    }

    // -------------------------------------------------------------------------
    // GET /share/{nodeId}
    // -------------------------------------------------------------------------

    @GetMapping("/share/{nodeId}")
    public ResponseEntity<Map<String, String>> getShare(@PathVariable String nodeId) {
        String share = shareStore.get(nodeId);
        if (share == null) {
            log.warn("Share not found for nodeId={}", nodeId);
            throw new ResponseStatusException(NOT_FOUND, "No share found for nodeId: " + nodeId);
        }
        log.debug("Retrieved share for nodeId={}", nodeId);
        return ResponseEntity.ok(Map.of("share", share));
    }

    // -------------------------------------------------------------------------
    // POST /sign/partial
    // -------------------------------------------------------------------------

    record PartialSignRequest(String txHash, String nodeId) {}

    @PostMapping("/sign/partial")
    public ResponseEntity<Map<String, String>> partialSign(@RequestBody PartialSignRequest req) {
        // Generate a random 64-byte (128 hex char) partial signature
        byte[] sigBytes = new byte[64];
        RANDOM.nextBytes(sigBytes);
        String partialSig = bytesToHex(sigBytes);
        log.debug("Produced partial signature for nodeId={} txHash={}", req.nodeId(), req.txHash());
        return ResponseEntity.ok(Map.of("partialSig", partialSig));
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
