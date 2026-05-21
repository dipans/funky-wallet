package com.funkywallet.repository;

import com.funkywallet.model.entity.Account;
import com.funkywallet.model.entity.Network;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByAddress(String address);
    List<Account> findAllByUserId(String userId);
    List<Account> findAllByNetwork(Network network);
    boolean existsByAddress(String address);
}
