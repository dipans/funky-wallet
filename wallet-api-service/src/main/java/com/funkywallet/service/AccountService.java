package com.funkywallet.service;

import com.funkywallet.client.chain.ChainAdapterClient;
import com.funkywallet.client.signing.KeyPairResponse;
import com.funkywallet.client.signing.SigningCoordinatorClient;
import com.funkywallet.exception.AccountNotFoundException;
import com.funkywallet.model.entity.Account;
import com.funkywallet.model.entity.Network;
import com.funkywallet.model.request.CreateAccountRequest;
import com.funkywallet.model.response.AccountResponse;
import com.funkywallet.model.response.BalanceResponse;
import com.funkywallet.model.response.CreateAccountResponse;
import com.funkywallet.repository.AccountRepository;
import com.funkywallet.util.ChainUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final SigningCoordinatorClient signingClient;
    private final ChainAdapterClient chainClient;

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Exclude Spring's fallback AnonymousAuthenticationToken — treat as no identity.
        // Istio rejects unauthenticated requests in prod before they reach this service.
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) return null;
        return auth.getName();
    }

    @Transactional
    public CreateAccountResponse createAccount(CreateAccountRequest request) {
        String userId = currentUserId();
        Network network = request.getNetwork();
        String mnemonic = null;
        try {
            mnemonic = signingClient.generateMnemonic();
            KeyPairResponse keyPair = signingClient.deriveKeyPair(mnemonic, network.name());

            Account account = new Account();
            account.setAddress(keyPair.getAddress());
            account.setPublicKey(keyPair.getPublicKey());
            account.setNetwork(network);
            account.setChainId(request.getChainId());
            account.setChainName(request.getChainName());
            account.setNetworkType(request.getNetworkType());
            account.setEnvironment(ChainUtil.deriveEnvironment(request.getNetworkType(), request.getChainId()));
            account.setUserId(userId);

            // For Solana: provision a durable nonce account so MPC signing rounds
            // don't race the 80-second recent-blockhash expiry window.
            if (network == Network.SOLANA) {
                String nonceAcct = chainClient.setupSolanaAccount(keyPair.getAddress());
                Map<String, Object> details = new HashMap<>();
                details.put("nonceAccount", nonceAcct);
                details.put("nonceAuthority", keyPair.getAddress()); // user wallet = authority initially
                account.setChainDetails(details);
            }

            account = accountRepository.save(account);

            AccountResponse resp = toResponse(account);
            String returnedMnemonic = mnemonic;
            mnemonic = null;
            return new CreateAccountResponse(resp, returnedMnemonic);
        } finally {
            mnemonic = null;
        }
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts() {
        String userId = currentUserId();
        if (userId == null) return List.of();
        return accountRepository.findAllByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String address) {
        String userId = currentUserId();
        Account account = accountRepository.findByAddress(address)
            .orElseThrow(() -> new AccountNotFoundException(address));
        if (userId != null && !userId.equals(account.getUserId()))
            throw new AccountNotFoundException(address);
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String address) {
        String userId = currentUserId();
        Account account = accountRepository.findByAddress(address)
            .orElseThrow(() -> new AccountNotFoundException(address));
        if (userId != null && !userId.equals(account.getUserId()))
            throw new AccountNotFoundException(address);

        BigDecimal balance = chainClient.getBalance(address, account.getNetwork().name());
        String symbol = symbolFor(account.getNetwork());

        return new BalanceResponse(address, account.getNetwork(), balance.toPlainString(), symbol, Instant.now());
    }

    private AccountResponse toResponse(Account a) {
        AccountResponse r = new AccountResponse();
        r.setId(a.getId());
        r.setAddress(a.getAddress());
        r.setPublicKey(a.getPublicKey());
        r.setNetwork(a.getNetwork());
        r.setChainId(a.getChainId());
        r.setChainName(a.getChainName());
        r.setNetworkType(a.getNetworkType());
        r.setEnvironment(a.getEnvironment());
        r.setChainDetails(a.getChainDetails());
        r.setCreatedAt(a.getCreatedAt());
        return r;
    }

    private String symbolFor(Network network) {
        return switch (network) {
            case ETHEREUM -> "ETH";
            case SOLANA   -> "SOL";
            case BITCOIN  -> "BTC";
        };
    }
}
