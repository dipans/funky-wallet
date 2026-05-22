package com.funkywallet.solanaadapter.service;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.SystemProgram;
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
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
public class SolanaService {

    private static final long LAMPORTS_PER_SOL = 1_000_000_000L;

    @Value("${solana.rpc.url}")
    private String rpcUrl;

    @Value("${solana.nonce-funder-keypair:}")
    private String nonceFunderKeypairBase58;

    private RpcClient rpcClient;

    // Sysvar addresses used in InitializeNonceAccount instruction
    private static final PublicKey SYSVAR_RECENT_BLOCKHASHES =
        new PublicKey("SysvarRecentB1ockHashes11111111111111111111111");
    private static final PublicKey SYSVAR_RENT =
        new PublicKey("SysvarRent111111111111111111111111111111111");
    private static final PublicKey SYSTEM_PROGRAM =
        new PublicKey("11111111111111111111111111111111");

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
            RpcSendTransactionConfig config = RpcSendTransactionConfig.builder()
                .encoding(RpcSendTransactionConfig.Encoding.base64)
                .build();
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
     * Creates a real durable nonce account on-chain for the given wallet address.
     *
     * The nonce account is funded by the account configured via SOLANA_NONCE_FUNDER_KEYPAIR.
     * Each nonce account costs ~0.00145 SOL (rent-exempt minimum for 80 bytes).
     *
     * Falls back to SHA-256 placeholder when no funder keypair is configured.
     */
    public String setupNonceAccount(String walletAddress) {
        if (nonceFunderKeypairBase58 == null || nonceFunderKeypairBase58.isBlank()) {
            log.warn("SOLANA_NONCE_FUNDER_KEYPAIR not set — using placeholder nonce address for {}", walletAddress);
            return derivePlaceholderNonceAddress(walletAddress);
        }
        try {
            return createRealNonceAccount(walletAddress);
        } catch (Exception e) {
            log.error("Failed to create real nonce account for {}: {}", walletAddress, e.getMessage());
            throw new RuntimeException("Nonce account creation failed: " + e.getMessage(), e);
        }
    }

    private String createRealNonceAccount(String walletAddress) throws Exception {
        // 1. Load funder keypair from env var (base58 64-byte: [private32|public32])
        byte[] funderKeyBytes = base58Decode(nonceFunderKeypairBase58);
        Account funder = new Account(funderKeyBytes);

        // 2. Generate a new random keypair for the nonce account
        byte[] noncePrivKey = new byte[32];
        new SecureRandom().nextBytes(noncePrivKey);
        byte[] noncePubKey = new Ed25519PrivateKeyParameters(noncePrivKey, 0)
            .generatePublicKey().getEncoded();
        byte[] nonceFullKey = new byte[64];
        System.arraycopy(noncePrivKey, 0, nonceFullKey, 0, 32);
        System.arraycopy(noncePubKey,  0, nonceFullKey, 32, 32);
        Account nonceAccount = new Account(nonceFullKey);

        // 3. Minimum lamports to keep nonce account rent-exempt (80 bytes of data)
        long lamports = rpcClient.getApi().getMinimumBalanceForRentExemption(80);

        // 4. Build transaction:
        //    - SystemProgram.createAccount (allocates and funds the nonce account)
        //    - SystemProgram.initializeNonce (marks it as a nonce account with walletAddress as authority)
        String blockhash = rpcClient.getApi().getLatestBlockhash().getValue().getBlockhash();
        PublicKey walletPubkey = new PublicKey(walletAddress);

        Transaction tx = new Transaction();
        tx.addInstruction(SystemProgram.createAccount(
            funder.getPublicKey(),
            nonceAccount.getPublicKey(),
            lamports,
            80,
            SYSTEM_PROGRAM
        ));
        tx.addInstruction(buildInitializeNonceInstruction(nonceAccount.getPublicKey(), walletPubkey));
        tx.setRecentBlockHash(blockhash);
        // Both funder (pays) and nonceAccount (proves ownership) must sign
        tx.sign(List.of(funder, nonceAccount));

        // 5. Broadcast and confirm
        RpcSendTransactionConfig config = RpcSendTransactionConfig.builder()
            .encoding(RpcSendTransactionConfig.Encoding.base64)
            .build();
        byte[] wireFormat = tx.serialize();
        String txHash = rpcClient.getApi().sendRawTransaction(
            Base64.getEncoder().encodeToString(wireFormat), config);

        String nonceAddress = nonceAccount.getPublicKey().toBase58();
        log.info("Created nonce account {} for wallet {} (tx: {})", nonceAddress, walletAddress, txHash);
        return nonceAddress;
    }

    /**
     * Builds the SystemProgram.InitializeNonceAccount instruction manually.
     * solanaj does not expose this as a static helper, so we construct it from raw bytes.
     *
     * Instruction layout (SystemProgram instruction index 6):
     *   bytes 0-3:  instruction index = 6 (little-endian u32)
     *   bytes 4-35: nonce authority public key (32 bytes)
     *
     * Required accounts:
     *   0. Nonce account (writable)
     *   1. SysvarRecentBlockhashes (read-only)
     *   2. SysvarRent (read-only)
     *   3. Nonce authority (read-only)
     */
    private TransactionInstruction buildInitializeNonceInstruction(
            PublicKey nonceAccount, PublicKey authority) {
        byte[] data = new byte[4 + 32];
        data[0] = 6; // instruction index for InitializeNonceAccount
        byte[] authorityBytes = authority.toByteArray();
        System.arraycopy(authorityBytes, 0, data, 4, 32);

        List<AccountMeta> keys = List.of(
            new AccountMeta(nonceAccount, false, true),
            new AccountMeta(SYSVAR_RECENT_BLOCKHASHES, false, false),
            new AccountMeta(SYSVAR_RENT, false, false),
            new AccountMeta(authority, false, false)
        );
        return new TransactionInstruction(SYSTEM_PROGRAM, keys, data);
    }

    /** Returns the public address of the configured nonce funder, or empty string if not set. */
    public String getNonceFunderAddress() {
        if (nonceFunderKeypairBase58 == null || nonceFunderKeypairBase58.isBlank()) return "";
        try {
            byte[] keyBytes = base58Decode(nonceFunderKeypairBase58);
            Account funder = new Account(keyBytes);
            return funder.getPublicKey().toBase58();
        } catch (Exception e) {
            log.warn("Could not derive nonce funder address: {}", e.getMessage());
            return "";
        }
    }

    private String derivePlaceholderNonceAddress(String walletAddress) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] derived = sha256.digest(
                ("solana-nonce:" + walletAddress).getBytes(StandardCharsets.UTF_8));
            return base58Encode(derived);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive placeholder nonce address", e);
        }
    }

    /** Exposes the RPC client for use by SolanaWatcherService. */
    public RpcClient getRpcClient() { return rpcClient; }

    public String getNodeInfo() {
        try {
            return rpcClient.getApi().getVersion().getSolanaCore();
        } catch (Exception e) {
            return "unavailable";
        }
    }

    // ── Base58 encode / decode ────────────────────────────────────────────────

    private static final char[] BASE58_ALPHABET =
        "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    static byte[] base58Decode(String input) {
        byte[] inputBytes = input.getBytes(StandardCharsets.US_ASCII);
        int[] digits = new int[inputBytes.length];
        for (int i = 0; i < inputBytes.length; i++) {
            int c = inputBytes[i];
            int digit = -1;
            if (c >= '1' && c <= '9') digit = c - '1';
            else if (c >= 'A' && c <= 'H') digit = c - 'A' + 9;
            else if (c >= 'J' && c <= 'N') digit = c - 'J' + 17;
            else if (c >= 'P' && c <= 'Z') digit = c - 'P' + 22;
            else if (c >= 'a' && c <= 'k') digit = c - 'a' + 33;
            else if (c >= 'm' && c <= 'z') digit = c - 'm' + 44;
            if (digit < 0) throw new IllegalArgumentException("Invalid base58 character: " + (char) c);
            digits[i] = digit;
        }
        // Count leading 1s (leading zero bytes)
        int leadingZeros = 0;
        for (byte b : inputBytes) { if (b == '1') leadingZeros++; else break; }
        // Convert base58 → base256
        byte[] output = new byte[inputBytes.length];
        int outputLen = 0;
        for (int digit : digits) {
            int carry = digit;
            for (int j = outputLen - 1; j >= 0; j--) {
                carry += 58 * (output[j] & 0xFF);
                output[j] = (byte) (carry & 0xFF);
                carry >>= 8;
            }
            while (carry > 0) {
                System.arraycopy(output, 0, output, 1, outputLen);
                output[0] = (byte) (carry & 0xFF);
                outputLen++;
                carry >>= 8;
            }
        }
        byte[] result = new byte[leadingZeros + outputLen];
        System.arraycopy(output, 0, result, leadingZeros, outputLen);
        return result;
    }

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
