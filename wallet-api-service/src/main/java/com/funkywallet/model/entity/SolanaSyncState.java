package com.funkywallet.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "solana_sync_state")
@Data
@NoArgsConstructor
public class SolanaSyncState {

    @Id
    @Column(length = 64)
    private String address;

    /** Most recent finalized signature processed; null = watcher has never seen this address */
    @Column(length = 128)
    private String lastSignature;

    @UpdateTimestamp
    private Instant updatedAt;
}
