package com.funkywallet.model.response;

import com.funkywallet.model.entity.Network;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
public class AccountResponse {
    private UUID id;
    private String address;
    private String publicKey;
    private Network network;
    private Integer chainId;
    private String chainName;
    private String networkType;
    private String environment;
    /** Network-specific details — present only for chains that need extra metadata (e.g. Solana). */
    private Map<String, Object> chainDetails;
    private Instant createdAt;
}
