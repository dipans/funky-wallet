package com.funkywallet.signingcoordinator.controller;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.programs.SystemProgram;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
public class SigningController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String TEST_MNEMONIC = "test test test test test test test test test test test junk";
    private static final String TEST_ADDRESS   = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

    @Value("${mnemonic.encryption.key:dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleXRlc3Q=}")
    private String encryptionKey;

    @Value("${geth.chain.id:1337}")
    private long defaultChainId;

    private MnemonicVault vault;

    @PostConstruct
    public void init() {
        this.vault = new MnemonicVault(encryptionKey);
        vault.store(TEST_ADDRESS, TEST_MNEMONIC);
    }

    // ── AES-256-GCM encrypted key vault ──────────────────────────────────────

    private static class MnemonicVault {
        private final SecretKey key;
        private final ConcurrentHashMap<String, byte[]> store = new ConcurrentHashMap<>();

        MnemonicVault(String base64Key) {
            try {
                byte[] raw = Base64.getDecoder().decode(base64Key);
                this.key = new SecretKeySpec(raw, "AES");
            } catch (Exception e) {
                throw new IllegalStateException("Invalid MNEMONIC_ENCRYPTION_KEY", e);
            }
        }

        void store(String address, String mnemonic) {
            try {
                byte[] nonce = new byte[12];
                new SecureRandom().nextBytes(nonce);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
                byte[] ct = cipher.doFinal(mnemonic.getBytes(StandardCharsets.UTF_8));
                byte[] blob = new byte[12 + ct.length];
                System.arraycopy(nonce, 0, blob, 0, 12);
                System.arraycopy(ct, 0, blob, 12, ct.length);
                store.put(normaliseAddress(address), blob);
            } catch (Exception e) {
                throw new RuntimeException("Encryption failed", e);
            }
        }

        String decrypt(String address) {
            byte[] blob = store.get(normaliseAddress(address));
            if (blob == null) throw new RuntimeException("No key material for address: " + address);
            try {
                byte[] nonce = Arrays.copyOfRange(blob, 0, 12);
                byte[] ct    = Arrays.copyOfRange(blob, 12, blob.length);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
                return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("Decryption failed", e);
            }
        }

        // Solana addresses are case-sensitive base58; EVM addresses are lowercased
        private static String normaliseAddress(String address) {
            return address.startsWith("0x") ? address.toLowerCase() : address;
        }
    }

    // ── BIP-39 word list (subset for mock) ───────────────────────────────────

    private static final String[] BIP39_WORDS = {
        "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
        "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid",
        "acoustic", "acquire", "across", "act", "action", "actor", "actress", "actual",
        "adapt", "add", "addict", "address", "adjust", "admit", "adult", "advance",
        "advice", "aerobic", "afford", "afraid", "again", "age", "agent", "agree",
        "ahead", "aim", "air", "airport", "aisle", "alarm", "album", "alcohol",
        "alert", "alien", "all", "alley", "allow", "almost", "alone", "alpha",
        "already", "also", "alter", "always", "amateur", "amazing", "among", "amount",
        "amused", "analyst", "anchor", "ancient", "anger", "angle", "angry", "animal",
        "ankle", "announce", "annual", "another", "answer", "antenna", "antique", "anxiety",
        "any", "apart", "apology", "appear", "apple", "approve", "april", "arcade",
        "arctic", "area", "arena", "argue", "arm", "armed", "armor", "army",
        "around", "arrange", "arrest", "arrive", "arrow", "art", "artefact", "artist",
        "artwork", "ask", "aspect", "assault", "asset", "assist", "assume", "asthma",
        "athlete", "atom", "attack", "attend", "attitude", "attract", "auction", "audit",
        "august", "aunt", "author", "auto", "autumn", "average", "avocado", "avoid",
        "awake", "aware", "away", "awesome", "awful", "awkward", "axis", "baby",
        "balance", "bamboo", "banana", "banner", "barely", "bargain", "barrel", "base",
        "basic", "basket", "battle", "beach", "bean", "beauty", "become", "beef",
        "before", "begin", "behave", "behind", "believe", "below", "belt", "bench",
        "benefit", "best", "betray", "better", "between", "beyond", "bicycle", "bid",
        "bike", "blind", "blood", "blossom", "blouse", "blue", "blur", "blush",
        "board", "boat", "body", "boil", "bomb", "bone", "book", "boost",
        "border", "boring", "borrow", "boss", "bottom", "bounce", "brain", "brand",
        "brave", "breeze", "brick", "bridge", "brief", "bright", "bring", "brisk",
        "bronze", "brother", "brown", "brush", "bubble", "buddy", "budget", "buffalo",
        "build", "bulb", "bulk", "bullet", "bundle", "bunker", "burden", "burger",
        "burst", "bus", "business", "busy", "butter", "buyer", "buzz", "cabbage",
        "cabin", "cable", "cactus", "cage", "cake", "call", "calm", "camera",
        "camp", "canal", "cancel", "candy", "cannon", "canyon", "capable", "capital",
        "captain", "car", "carbon", "cargo", "carpet", "carry", "cart", "case",
        "cash", "castle", "casual", "cat", "catalog", "catch", "category", "cattle"
    };

    // ── POST /mnemonic/generate ───────────────────────────────────────────────

    @PostMapping("/mnemonic/generate")
    public ResponseEntity<Map<String, String>> generateMnemonic() {
        List<String> words = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            words.add(BIP39_WORDS[RANDOM.nextInt(BIP39_WORDS.length)]);
        }
        return ResponseEntity.ok(Map.of("mnemonic", String.join(" ", words)));
    }

    // ── POST /keypair/derive ──────────────────────────────────────────────────

    record DeriveRequest(String mnemonic, String network) {}

    @PostMapping("/keypair/derive")
    public ResponseEntity<Map<String, String>> deriveKeypair(@RequestBody DeriveRequest req) {
        try {
            if ("SOLANA".equalsIgnoreCase(req.network())) {
                return deriveSolanaKeypair(req.mnemonic());
            }
            return deriveEvmKeypair(req.mnemonic());
        } catch (Exception e) {
            log.error("Key derivation failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<Map<String, String>> deriveEvmKeypair(String mnemonic) {
        Credentials credentials = deriveEvmCredentials(mnemonic);
        String address   = credentials.getAddress();
        String publicKey = Numeric.toHexStringWithPrefix(credentials.getEcKeyPair().getPublicKey());
        vault.store(address, mnemonic);
        log.debug("Derived EVM keypair address={}", address);
        return ResponseEntity.ok(Map.of("address", address, "publicKey", publicKey));
    }

    private ResponseEntity<Map<String, String>> deriveSolanaKeypair(String mnemonic) throws Exception {
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, "");
        byte[] keyMaterial = slip0010Derive(seed, new int[]{
            0x8000002C, // 44'
            0x800001F5, // 501' (Solana)
            0x80000000, // 0'
            0x80000000  // 0'
        });
        byte[] privateKeyBytes = Arrays.copyOfRange(keyMaterial, 0, 32);
        Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(privateKeyBytes, 0);
        byte[] publicKeyBytes = privateKey.generatePublicKey().getEncoded();

        String address   = base58Encode(publicKeyBytes);
        String publicKey = base58Encode(publicKeyBytes);
        vault.store(address, mnemonic);
        log.debug("Derived Solana keypair address={}", address);
        return ResponseEntity.ok(Map.of("address", address, "publicKey", publicKey));
    }

    // ── POST /transaction/sign ────────────────────────────────────────────────

    record SignRequest(String accountAddress, String unsignedTx, String network, Long chainId) {}

    @PostMapping("/transaction/sign")
    public ResponseEntity<Map<String, String>> signTransaction(@RequestBody SignRequest req) {
        String mnemonic = null;
        try {
            mnemonic = vault.decrypt(req.accountAddress());

            if ("SOLANA".equalsIgnoreCase(req.network())) {
                return signSolanaTransaction(mnemonic, req.unsignedTx());
            }
            return signEvmTransaction(mnemonic, req.unsignedTx(), req.chainId());

        } catch (Exception e) {
            log.error("Signing failed for account={}: {}", req.accountAddress(), e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Signing failed: " + e.getMessage()));
        } finally {
            mnemonic = null;
        }
    }

    private ResponseEntity<Map<String, String>> signEvmTransaction(
            String mnemonic, String unsignedTx, Long chainId) throws Exception {
        long cid = chainId != null ? chainId : defaultChainId;
        String txHex = unsignedTx.contains("|") ? unsignedTx.split("\\|")[1] : unsignedTx;
        Credentials credentials = deriveEvmCredentials(mnemonic);
        RawTransaction rawTx = TransactionDecoder.decode(txHex);
        byte[] signedBytes = TransactionEncoder.signMessage(rawTx, cid, credentials);
        return ResponseEntity.ok(Map.of("signedTx", Numeric.toHexString(signedBytes)));
    }

    /**
     * Builds, signs, and serializes a Solana transfer transaction.
     *
     * Input:  unsignedTx = "from|to|lamports|recentBlockhash" (from solana-chain-adapter)
     * Output: signedTx   = base64(Solana wire format) — ready for sendRawTransaction
     *
     * Uses solanaj to construct the Transaction and sign with the ed25519 key
     * derived via SLIP-0010 from the BIP-39 mnemonic.
     */
    private ResponseEntity<Map<String, String>> signSolanaTransaction(
            String mnemonic, String unsignedTx) throws Exception {
        String[] parts = unsignedTx.split("\\|");
        if (parts.length < 4) throw new RuntimeException("Invalid Solana unsignedTx format");

        PublicKey from         = new PublicKey(parts[0]);
        PublicKey to           = new PublicKey(parts[1]);
        long lamports          = Long.parseLong(parts[2]);
        String recentBlockhash = parts[3];

        // Derive ed25519 private key via SLIP-0010 m/44'/501'/0'/0'
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, "");
        byte[] keyMaterial = slip0010Derive(seed, new int[]{
            0x8000002C, // 44'
            0x800001F5, // 501'
            0x80000000, // 0'
            0x80000000  // 0'
        });
        byte[] privateKeyBytes = Arrays.copyOfRange(keyMaterial, 0, 32);
        byte[] publicKeyBytes  = new Ed25519PrivateKeyParameters(privateKeyBytes, 0)
            .generatePublicKey().getEncoded();

        // solanaj Account expects 64-byte key: [private 32 | public 32]
        byte[] fullKey = new byte[64];
        System.arraycopy(privateKeyBytes, 0, fullKey, 0, 32);
        System.arraycopy(publicKeyBytes,  0, fullKey, 32, 32);
        Account signer = new Account(fullKey);

        // Build and sign the transaction; solanaj uses first signer as fee payer
        Transaction tx = new Transaction();
        tx.addInstruction(SystemProgram.transfer(from, to, lamports));
        tx.setRecentBlockHash(recentBlockhash);
        tx.sign(List.of(signer));

        byte[] wireFormat = tx.serialize();
        return ResponseEntity.ok(Map.of("signedTx", Base64.getEncoder().encodeToString(wireFormat)));
    }

    // ── SLIP-0010 ed25519 key derivation ──────────────────────────────────────

    /**
     * Derives an ed25519 key via SLIP-0010 from a BIP-39 seed.
     * All indices must be hardened (bit 31 set); ed25519 does not support
     * unhardened child keys.
     *
     * Returns 64 bytes: [0..31] private key, [32..63] chain code.
     */
    private static byte[] slip0010Derive(byte[] seed, int[] path) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("ed25519 seed".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] I = mac.doFinal(seed);

        for (int index : path) {
            byte[] Il = Arrays.copyOfRange(I, 0, 32);  // private key
            byte[] Ir = Arrays.copyOfRange(I, 32, 64); // chain code

            mac.init(new SecretKeySpec(Ir, "HmacSHA512"));
            byte[] data = new byte[37];
            data[0] = 0x00; // hardened child: 0x00 || private_key || index
            System.arraycopy(Il, 0, data, 1, 32);
            data[33] = (byte) ((index >> 24) & 0xFF);
            data[34] = (byte) ((index >> 16) & 0xFF);
            data[35] = (byte) ((index >> 8)  & 0xFF);
            data[36] = (byte) (index & 0xFF);
            I = mac.doFinal(data);
        }
        return I;
    }

    // ── EVM helpers ───────────────────────────────────────────────────────────

    private static Credentials deriveEvmCredentials(String mnemonic) {
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, "");
        Bip32ECKeyPair master = Bip32ECKeyPair.generateKeyPair(seed);
        int[] path = {
            44  | Bip32ECKeyPair.HARDENED_BIT,
            60  | Bip32ECKeyPair.HARDENED_BIT,
            0   | Bip32ECKeyPair.HARDENED_BIT,
            0, 0
        };
        return Credentials.create(Bip32ECKeyPair.deriveKeyPair(master, path));
    }

    // ── Base58 encoding ───────────────────────────────────────────────────────

    private static final char[] BASE58_ALPHABET =
        "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    private static String base58Encode(byte[] input) {
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
