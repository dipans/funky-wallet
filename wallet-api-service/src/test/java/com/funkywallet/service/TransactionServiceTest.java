package com.funkywallet.service;

import com.funkywallet.client.chain.ChainAdapterClient;
import com.funkywallet.client.signing.SigningCoordinatorClient;
import com.funkywallet.exception.AccountNotFoundException;
import com.funkywallet.model.entity.Account;
import com.funkywallet.model.entity.Network;
import com.funkywallet.model.entity.Transaction;
import com.funkywallet.model.entity.TransactionStatus;
import com.funkywallet.model.request.SendTransactionRequest;
import com.funkywallet.model.response.TransactionResponse;
import com.funkywallet.repository.AccountRepository;
import com.funkywallet.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock AccountRepository accountRepository;
    @Mock SigningCoordinatorClient signingClient;
    @Mock ChainAdapterClient chainClient;

    @InjectMocks TransactionService transactionService;

    @Test
    void sendTransaction_returnsPendingTransaction() {
        Account mockAccount = new Account();
        mockAccount.setAddress("0xFROM");
        mockAccount.setNetwork(Network.ETHEREUM);
        mockAccount.setChainId(1337);
        when(accountRepository.findByAddress("0xFROM")).thenReturn(Optional.of(mockAccount));
        when(chainClient.buildUnsignedTx(any(), any(), any(), any())).thenReturn("unsignedHex");
        when(signingClient.signTransaction(any(), any(), any(), any())).thenReturn("signedHex");
        when(chainClient.broadcast(any(), any())).thenReturn("0xHASH");

        Transaction saved = new Transaction();
        saved.setId(UUID.randomUUID());
        saved.setHash("0xHASH");
        saved.setFromAddress("0xFROM");
        saved.setToAddress("0xTO");
        saved.setAmount(BigDecimal.ONE);
        saved.setSymbol("ETH");
        saved.setNetwork(Network.ETHEREUM);
        saved.setStatus(TransactionStatus.PENDING);
        when(transactionRepository.save(any())).thenReturn(saved);

        SendTransactionRequest req = new SendTransactionRequest();
        req.setFromAddress("0xFROM");
        req.setToAddress("0xTO");
        req.setAmount(BigDecimal.ONE);
        req.setNetwork(Network.ETHEREUM);

        TransactionResponse resp = transactionService.sendTransaction(req);

        assertThat(resp.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(resp.getHash()).isEqualTo("0xHASH");
        verify(signingClient).signTransaction(any(), eq("unsignedHex"), eq("ETHEREUM"), any());
    }

    @Test
    void sendTransaction_throwsWhenAccountNotFound() {
        when(accountRepository.findByAddress("0xUNKNOWN")).thenReturn(Optional.empty());

        SendTransactionRequest req = new SendTransactionRequest();
        req.setFromAddress("0xUNKNOWN");
        req.setToAddress("0xTO");
        req.setAmount(BigDecimal.ONE);
        req.setNetwork(Network.ETHEREUM);

        assertThatThrownBy(() -> transactionService.sendTransaction(req))
            .isInstanceOf(AccountNotFoundException.class);
    }
}
