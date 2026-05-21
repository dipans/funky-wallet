package com.funkywallet.repository;

import com.funkywallet.model.entity.Transaction;
import com.funkywallet.model.entity.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findAllByFromAddressOrderByCreatedAtDesc(String fromAddress);
    Page<Transaction> findAllByFromAddressOrderByCreatedAtDesc(String fromAddress, Pageable pageable);
    Page<Transaction> findAllByFromAddressInOrderByCreatedAtDesc(List<String> addresses, Pageable pageable);
    Optional<Transaction> findByHash(String hash);
    boolean existsByHash(String hash);
    /** Direction-aware deduplication: allows one SENT + one RECEIVED per on-chain tx hash. */
    boolean existsByHashAndFromAddressAndStatus(String hash, String fromAddress, TransactionStatus status);
    boolean existsByHashAndToAddressAndStatus(String hash, String toAddress, TransactionStatus status);

    @Query("SELECT t FROM Transaction t WHERE t.fromAddress IN :addresses OR t.toAddress IN :addresses ORDER BY t.createdAt DESC")
    Page<Transaction> findAllByFromOrToAddressInOrderByCreatedAtDesc(@Param("addresses") List<String> addresses, Pageable pageable);
}
