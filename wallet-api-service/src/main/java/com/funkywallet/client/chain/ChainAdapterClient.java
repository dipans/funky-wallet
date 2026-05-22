package com.funkywallet.client.chain;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ChainAdapterClient {

    private final WebClient evmClient;
    private final WebClient solanaClient;

    public ChainAdapterClient(
            @Qualifier("chainWebClient") WebClient evmClient,
            @Qualifier("solanaChainWebClient") WebClient solanaClient) {
        this.evmClient = evmClient;
        this.solanaClient = solanaClient;
    }

    private WebClient clientFor(String network) {
        return "SOLANA".equalsIgnoreCase(network) ? solanaClient : evmClient;
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    @CircuitBreaker(name = "chain", fallbackMethod = "getBalanceFallback")
    public BigDecimal getBalance(String address, String network) {
        return clientFor(network).get()
            .uri(u -> u.path("/balance").queryParam("address", address).queryParam("network", network).build())
            .retrieve()
            .bodyToMono(BalanceData.class)
            .map(BalanceData::getAmount)
            .block();
    }

    // ── Build unsigned tx ─────────────────────────────────────────────────────

    @CircuitBreaker(name = "chain", fallbackMethod = "buildUnsignedTxFallback")
    public String buildUnsignedTx(String from, String to, BigDecimal amount, String network) {
        return clientFor(network).post()
            .uri("/tx/build")
            .bodyValue(Map.of("from", from, "to", to, "amount", amount, "network", network))
            .retrieve()
            .bodyToMono(UnsignedTxResponse.class)
            .map(UnsignedTxResponse::getUnsignedTx)
            .block();
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    @CircuitBreaker(name = "chain", fallbackMethod = "broadcastFallback")
    public String broadcast(String signedTx, String network) {
        return clientFor(network).post()
            .uri("/tx/broadcast")
            .bodyValue(Map.of("signedTx", signedTx, "network", network))
            .retrieve()
            .bodyToMono(BroadcastResponse.class)
            .map(BroadcastResponse::getTxHash)
            .block();
    }

    // ── Solana block watcher ──────────────────────────────────────────────────

    /**
     * Returns finalized incoming SOL transactions for the given address that are
     * newer than lastSignature. Used by BlockWatcherService.
     *
     * @param address       Solana wallet address to poll
     * @param lastSignature last signature already processed (null = first run)
     */
    public List<SolanaIncomingTx> getSolanaNewTransactions(String address, String lastSignature) {
        try {
            String uri = lastSignature != null
                ? "/account/" + address + "/new-transactions?since=" + lastSignature
                : "/account/" + address + "/new-transactions";
            return solanaClient.get()
                .uri(uri)
                .retrieve()
                .bodyToFlux(SolanaIncomingTx.class)
                .collectList()
                .block();
        } catch (Exception e) {
            log.warn("Solana watcher fetch failed for {}: {}", address, e.getMessage());
            return List.of();
        }
    }

    // ── Solana nonce funder ───────────────────────────────────────────────────

    /** Returns the public address of the nonce funder configured in the Solana chain adapter. */
    public String getSolanaNonceFunderAddress() {
        try {
            return solanaClient.get()
                .uri("/nonce/funder")
                .retrieve()
                .bodyToMono(NonceFunderResponse.class)
                .map(r -> r.address() != null ? r.address() : "")
                .block();
        } catch (Exception e) {
            log.warn("Could not fetch Solana nonce funder: {}", e.getMessage());
            return "";
        }
    }

    // ── Solana account setup ──────────────────────────────────────────────────

    /**
     * Called during Solana account creation to provision a durable nonce account.
     * Returns the nonce account address to store alongside the wallet address.
     */
    public String setupSolanaAccount(String walletAddress) {
        return solanaClient.post()
            .uri("/account/setup")
            .bodyValue(Map.of("walletAddress", walletAddress))
            .retrieve()
            .bodyToMono(SetupResponse.class)
            .map(SetupResponse::getNonceAccount)
            .block();
    }

    // ── EVM block endpoints (used by BlockWatcherService) ────────────────────

    public BlockInfo getLatestBlock() {
        return evmClient.get()
            .uri("/block/latest")
            .retrieve()
            .bodyToMono(BlockInfo.class)
            .block();
    }

    public List<TxInfo> getBlockTransactions(long blockNumber) {
        try {
            return evmClient.get()
                .uri("/block/{blockNumber}/transactions", blockNumber)
                .retrieve()
                .bodyToFlux(TxInfo.class)
                .collectList()
                .block();
        } catch (Exception e) {
            log.warn("Failed to fetch transactions for block {}: {}", blockNumber, e.getMessage());
            return List.of();
        }
    }

    // ── Fallbacks ─────────────────────────────────────────────────────────────

    private String broadcastFallback(String signedTx, String network, Exception e) {
        log.error("Chain adapter unavailable for broadcast ({}): {}", network, e.getMessage());
        throw new RuntimeException("Chain adapter unavailable");
    }

    private BigDecimal getBalanceFallback(String address, String network, Exception e) {
        log.error("Chain adapter unavailable for balance ({}): {}", network, e.getMessage());
        return BigDecimal.ZERO;
    }

    private String buildUnsignedTxFallback(String from, String to, BigDecimal amount, String network, Exception e) {
        log.error("Chain adapter unavailable for tx build ({}): {}", network, e.getMessage());
        throw new RuntimeException("Chain adapter unavailable");
    }

    // ── Response records ──────────────────────────────────────────────────────

    public record BlockInfo(long blockNumber, String blockHash) {}

    public record TxInfo(String hash, String fromAddress, String toAddress, String value, String blockHash) {}

    public record BroadcastResponse(String txHash) {
        public String getTxHash() { return txHash; }
    }

    public record BalanceData(BigDecimal amount, String symbol) {
        public BigDecimal getAmount() { return amount; }
    }

    public record UnsignedTxResponse(String unsignedTx) {
        public String getUnsignedTx() { return unsignedTx; }
    }

    public record SetupResponse(String nonceAccount) {
        public String getNonceAccount() { return nonceAccount; }
    }

    public record NonceFunderResponse(String address, boolean configured) {}

    public record SolanaIncomingTx(
        String signature,
        String fromAddress,
        String toAddress,
        java.math.BigDecimal amount,
        long blockTime,
        String direction   // "RECEIVED" or "SENT"
    ) {}
}
