package com.funkywallet.service;

import com.funkywallet.client.chain.ChainAdapterClient;
import com.funkywallet.client.signing.SigningCoordinatorClient;
import com.funkywallet.exception.AccountNotFoundException;
import com.funkywallet.exception.TransactionNotFoundException;
import com.funkywallet.model.entity.Account;
import com.funkywallet.model.entity.Transaction;
import com.funkywallet.model.entity.TransactionStatus;
import com.funkywallet.model.request.SendTransactionRequest;
import com.funkywallet.model.response.PagedResponse;
import com.funkywallet.model.response.TransactionResponse;
import com.funkywallet.repository.AccountRepository;
import com.funkywallet.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final SigningCoordinatorClient signingClient;
    private final ChainAdapterClient chainClient;

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) return null;
        return auth.getName();
    }

    @Transactional
    public TransactionResponse sendTransaction(SendTransactionRequest request) {
        Account account = accountRepository.findByAddress(request.getFromAddress())
            .orElseThrow(() -> new AccountNotFoundException(request.getFromAddress()));

        String userId = currentUserId();
        if (userId != null && !userId.equals(account.getUserId()))
            throw new AccountNotFoundException(request.getFromAddress());

        String unsignedTx = chainClient.buildUnsignedTx(
            request.getFromAddress(), request.getToAddress(),
            request.getAmount(), request.getNetwork().name()
        );

        String signedTx = signingClient.signTransaction(
            request.getFromAddress(), unsignedTx, request.getNetwork().name(), account.getChainId());

        String txHash = chainClient.broadcast(signedTx, request.getNetwork().name());

        Transaction tx = new Transaction();
        tx.setHash(txHash);
        tx.setFromAddress(request.getFromAddress());
        tx.setToAddress(request.getToAddress());
        tx.setAmount(request.getAmount());
        tx.setSymbol(symbolFor(request.getNetwork().name()));
        tx.setNetwork(request.getNetwork());
        tx.setStatus(TransactionStatus.PENDING);
        tx = transactionRepository.save(tx);

        confirmTransactionAsync(tx.getId());
        return toResponse(tx);
    }

    @Async
    public void confirmTransactionAsync(UUID txId) {
        try {
            Thread.sleep(3000); // simulate block time
            transactionRepository.findById(txId).ifPresent(tx -> {
                tx.setStatus(TransactionStatus.CONFIRMED);
                tx.setConfirmedAt(Instant.now());
                transactionRepository.save(tx);
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Confirmation interrupted for tx {}", txId);
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> listTransactions(String address, int page, int size) {
        String userId = currentUserId();
        PageRequest pageable = PageRequest.of(page, size);
        Page<Transaction> result;

        if (address != null) {
            // Verify the address belongs to the current user
            Account account = accountRepository.findByAddress(address)
                .orElseThrow(() -> new AccountNotFoundException(address));
            if (userId != null && !userId.equals(account.getUserId()))
                throw new AccountNotFoundException(address);
            result = transactionRepository.findAllByFromOrToAddressInOrderByCreatedAtDesc(List.of(address), pageable);
        } else {
            // No address specified — return transactions for all of the user's accounts
            if (userId == null) {
                return new PagedResponse<>(List.of(), page, size, 0L, 0);
            }
            List<String> userAddresses = accountRepository.findAllByUserId(userId)
                .stream().map(Account::getAddress).toList();
            if (userAddresses.isEmpty()) {
                return new PagedResponse<>(List.of(), page, size, 0L, 0);
            }
            result = transactionRepository.findAllByFromOrToAddressInOrderByCreatedAtDesc(userAddresses, pageable);
        }

        return new PagedResponse<>(
            result.getContent().stream().map(this::toResponse).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    @Transactional
    public TransactionResponse confirmTransaction(String id) {
        Transaction tx = transactionRepository.findById(UUID.fromString(id))
            .orElseThrow(() -> new TransactionNotFoundException(id));
        tx.setStatus(TransactionStatus.CONFIRMED);
        tx.setConfirmedAt(Instant.now());
        return toResponse(transactionRepository.save(tx));
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(String id) {
        return transactionRepository.findById(UUID.fromString(id))
            .map(this::toResponse)
            .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    private TransactionResponse toResponse(Transaction t) {
        TransactionResponse r = new TransactionResponse();
        r.setId(t.getId());
        r.setHash(t.getHash());
        r.setFromAddress(t.getFromAddress());
        r.setToAddress(t.getToAddress());
        r.setAmount(t.getAmount());
        r.setSymbol(t.getSymbol());
        r.setNetwork(t.getNetwork());
        r.setStatus(t.getStatus());
        r.setCreatedAt(t.getCreatedAt());
        r.setConfirmedAt(t.getConfirmedAt());
        return r;
    }

    private String symbolFor(String network) {
        return switch (network) {
            case "ETHEREUM" -> "ETH";
            case "SOLANA"   -> "SOL";
            case "BITCOIN"  -> "BTC";
            default         -> network;
        };
    }
}
