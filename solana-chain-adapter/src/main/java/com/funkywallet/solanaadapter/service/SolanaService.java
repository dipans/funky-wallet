package com.funkywallet.solanaadapter.service;

import lombok.extern.slf4j.Slf4j;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.config.RpcSendTransactionConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
@Slf4j
public class SolanaService {

    private static final long LAMPORTS_PER_SOL = 1_000_000_000L;

    @Value("${solana.rpc.url}")
    private String rpcUrl;

    private RpcClient rpcClient;

    @PostConstruct
    public void init() {
        rpcClient = new RpcClient(rpcUrl);
        log.info("Solana chain adapter connected to {}", rpcUrl);
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    public BigDecimal getBalance(String address) {
        try {
            long lamports = rpcClient.getApi().getBalance(new PublicKey(address));
            return BigDecimal.valueOf(lamports)
                .divide(BigDecimal.valueOf(LAMPORTS_PER_SOL), 9, RoundingMode.HALF_UP);
        } catch (RpcException e) {
            log.error("Failed to get Solana balance for {}: {}", address, e.getMessage());
            throw new RuntimeException("Failed to get balance", e);
        }
    }

    // ── Build unsigned tx ─────────────────────────────────────────────────────

    /**
     * Returns transaction parameters as a pipe-delimited string:
     *   "from|to|lamports|recentBlockhash"
     *
     * The signing coordinator parses this, builds, signs, and serializes the full
     * Solana transaction using solanaj. This keeps all crypto in the coordinator.
     *
     * NOTE: Uses recentBlockhash (expires ~80s). For production MPC wallets, replace
     * with a durable nonce account so signing rounds don't race the expiry window:
     *   1. Fetch nonceAccount state: rpcClient.getApi().getNonce(nonceAccountPubkey)
     *   2. Use stored nonce as blockhash in the string
     *   3. Signing coordinator must prepend SystemProgram.nonceAdvance as first instruction
     */
    public String buildUnsignedTx(String from, String to, BigDecimal amountSol) {
        try {
            String blockhash = rpcClient.getApi().getLatestBlockhash().getValue().getBlockhash();
            long lamports = amountSol.multiply(BigDecimal.valueOf(LAMPORTS_PER_SOL)).longValue();
            return from + "|" + to + "|" + lamports + "|" + blockhash;
        } catch (RpcException e) {
            log.error("Failed to build Solana unsigned tx: {}", e.getMessage());
            throw new RuntimeException("Failed to build unsigned tx", e);
        }
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    /**
     * Accepts a fully signed Solana transaction in base64 wire format
     * (as returned by the signing coordinator) and sends it via RPC.
     */
    public String broadcast(String signedTxBase64) {
        try {
            RpcSendTransactionConfig config = new RpcSendTransactionConfig();
            config.setEncoding(RpcSendTransactionConfig.Encoding.base64);
            String txHash = rpcClient.getApi().sendRawTransaction(signedTxBase64, config);
            log.info("Broadcast Solana tx: {}", txHash);
            return txHash;
        } catch (RpcException e) {
            log.error("Failed to broadcast Solana tx: {}", e.getMessage());
            throw new RuntimeException("Broadcast error: " + e.getMessage(), e);
        }
    }

    // ── Nonce account setup ───────────────────────────────────────────────────

    /**
     * Returns a deterministic nonce account address for the given wallet address.
     *
     * Dev/mock: SHA-256 derived — no on-chain tx required.
     * Production: create a real nonce account funded with ~0.00144 SOL:
     *   SystemProgram.createAccount + SystemProgram.initializeNonce(nonceAccount, nonceAuthority=walletAddress)
     */
    public String setupNonceAccount(String walletAddress) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] derived = sha256.digest(
                ("solana-nonce:" + walletAddress).getBytes(StandardCharsets.UTF_8));
            return base58Encode(derived);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive nonce account address", e);
        }
    }

    public String getNodeInfo() {
        try {
            return rpcClient.getApi().getVersion().getSolanaCore();
        } catch (Exception e) {
            return "unavailable";
        }
    }

    // ── Base58 encoding ───────────────────────────────────────────────────────

    private static final char[] BASE58_ALPHABET =
        "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    static String base58Encode(byte[] input) {
        int[] digits = new int[input.length * 137 / 100 + 1];
        int digitLen = 1;
        for (byte b : input) {
            int carry = b & 0xFF;
            for (int i = 0; i < digitLen; i++) {
                carry += digits[i] << 8;
                digits[i] = carry % 58;
                carry /= 58;
            }
            while (carry > 0) {
                digits[digitLen++] = carry % 58;
                carry /= 58;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : input) {
            if (b == 0) sb.append('1');
            else break;
        }
        for (int i = digitLen - 1; i >= 0; i--) {
            sb.append(BASE58_ALPHABET[digits[i]]);
        }
        return sb.toString();
    }
}
