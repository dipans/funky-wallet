package com.funkywallet.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String address;

    @Column(nullable = false, length = 256)
    private String publicKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Network network;

    @Column(nullable = false)
    private Integer chainId = 0;

    @Column(nullable = false, length = 64)
    private String chainName = "Unknown";

    @Column(nullable = false, length = 16)
    private String networkType = "EVM";

    @Column(nullable = false, length = 16)
    private String environment = "LOCAL";

    @Column(length = 128)
    private String userId;

    /**
     * Network-specific details stored as a JSON string.
     * Solana: { "nonceAccount": "...", "nonceAuthority": "..." }
     * EVM:    null
     * Bitcoin (future): { "xpub": "...", "addressType": "p2wpkh" }
     */
    @Convert(converter = ChainDetailsConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> chainDetails;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
