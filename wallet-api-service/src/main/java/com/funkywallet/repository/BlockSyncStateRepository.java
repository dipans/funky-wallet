package com.funkywallet.repository;

import com.funkywallet.model.entity.BlockSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BlockSyncStateRepository extends JpaRepository<BlockSyncState, UUID> {
    Optional<BlockSyncState> findByNetwork(String network);
}
