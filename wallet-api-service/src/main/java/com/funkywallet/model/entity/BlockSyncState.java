package com.funkywallet.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "block_sync_state")
@Data
@NoArgsConstructor
public class BlockSyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String network;

    @Column(nullable = false)
    private Long lastProcessedBlock = 0L;

    @Column(length = 66)
    private String lastProcessedBlockHash;

    @UpdateTimestamp
    private Instant updatedAt;
}
