package com.funkywallet.client.signing;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@Slf4j
public class SigningCoordinatorClient {

    private final WebClient webClient;

    public SigningCoordinatorClient(@Qualifier("signingWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @CircuitBreaker(name = "signing", fallbackMethod = "generateMnemonicFallback")
    public String generateMnemonic() {
        return webClient.post()
            .uri("/mnemonic/generate")
            .retrieve()
            .bodyToMono(MnemonicResponse.class)
            .map(MnemonicResponse::getMnemonic)
            .block();
    }

    @CircuitBreaker(name = "signing", fallbackMethod = "deriveKeyPairFallback")
    public KeyPairResponse deriveKeyPair(String mnemonic, String network) {
        String m = mnemonic;
        try {
            return webClient.post()
                .uri("/keypair/derive")
                .bodyValue(Map.of("mnemonic", m, "network", network))
                .retrieve()
                .bodyToMono(KeyPairResponse.class)
                .block();
        } finally {
            m = null;
        }
    }

    @CircuitBreaker(name = "signing", fallbackMethod = "signTransactionFallback")
    public String signTransaction(String accountAddress, String unsignedTx, String network, Integer chainId) {
        return webClient.post()
            .uri("/transaction/sign")
            .bodyValue(Map.of(
                "accountAddress", accountAddress,
                "unsignedTx", unsignedTx,
                "network", network,
                "chainId", chainId != null ? chainId : 1337
            ))
            .retrieve()
            .bodyToMono(SignResponse.class)
            .map(SignResponse::getSignedTx)
            .block();
    }

    private String generateMnemonicFallback(Exception e) {
        log.error("Signing coordinator unavailable: {}", e.getMessage());
        throw new com.funkywallet.exception.SigningException("Signing service unavailable");
    }

    private KeyPairResponse deriveKeyPairFallback(String mnemonic, String network, Exception e) {
        throw new com.funkywallet.exception.SigningException("Signing service unavailable");
    }

    private String signTransactionFallback(String accountAddress, String unsignedTx, String network, Integer chainId, Exception e) {
        throw new com.funkywallet.exception.SigningException("Signing service unavailable");
    }

    public record MnemonicResponse(String mnemonic) {
        public String getMnemonic() { return mnemonic; }
    }

    public record SignResponse(String signedTx) {
        public String getSignedTx() { return signedTx; }
    }
}
