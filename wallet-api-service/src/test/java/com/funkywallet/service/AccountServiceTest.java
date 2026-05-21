package com.funkywallet.service;

import com.funkywallet.client.chain.ChainAdapterClient;
import com.funkywallet.client.signing.KeyPairResponse;
import com.funkywallet.client.signing.SigningCoordinatorClient;
import com.funkywallet.exception.AccountNotFoundException;
import com.funkywallet.model.entity.Account;
import com.funkywallet.model.entity.Network;
import com.funkywallet.model.request.CreateAccountRequest;
import com.funkywallet.model.response.CreateAccountResponse;
import com.funkywallet.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock SigningCoordinatorClient signingClient;
    @Mock ChainAdapterClient chainClient;

    @InjectMocks AccountService accountService;

    @Test
    void createAccount_returnsMnemonicOnce() {
        KeyPairResponse kp = new KeyPairResponse();
        kp.setAddress("0xABC");
        kp.setPublicKey("pubkey");

        Account saved = new Account();
        saved.setAddress("0xABC");
        saved.setPublicKey("pubkey");
        saved.setNetwork(Network.ETHEREUM);
        saved.setChainId(560048);
        saved.setChainName("Hoodi Testnet");
        saved.setNetworkType("EVM");
        saved.setEnvironment("TESTNET");

        when(signingClient.generateMnemonic()).thenReturn("word1 word2 word3");
        when(signingClient.deriveKeyPair(any(), eq("ETHEREUM"))).thenReturn(kp);
        when(accountRepository.save(any())).thenReturn(saved);

        CreateAccountRequest request = new CreateAccountRequest();
        request.setNetwork(Network.ETHEREUM);
        request.setChainId(560048);
        request.setChainName("Hoodi Testnet");
        request.setNetworkType("EVM");

        CreateAccountResponse resp = accountService.createAccount(request);

        assertThat(resp.getMnemonic()).isEqualTo("word1 word2 word3");
        assertThat(resp.getAccount().getAddress()).isEqualTo("0xABC");
    }

    @Test
    void getBalance_returnsZeroWhenChainUnavailable() {
        Account acc = new Account();
        acc.setAddress("0xABC");
        acc.setNetwork(Network.ETHEREUM);

        when(accountRepository.findByAddress("0xABC")).thenReturn(Optional.of(acc));
        when(chainClient.getBalance("0xABC", "ETHEREUM")).thenReturn(BigDecimal.ZERO);

        var balance = accountService.getBalance("0xABC");
        assertThat(balance.getAmount()).isEqualTo("0");
    }

    @Test
    void getAccount_throwsWhenNotFound() {
        when(accountRepository.findByAddress("unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> accountService.getAccount("unknown"))
            .isInstanceOf(AccountNotFoundException.class);
    }
}
