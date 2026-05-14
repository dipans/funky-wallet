package com.funkywallet.solanaadapter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.types.ConfirmedTransaction;
import org.p2p.solanaj.rpc.types.SignatureInformation;
import org.p2p.solanaj.rpc.types.config.Commitment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SolanaWatcherService {

    private static final long LAMPORTS_PER_SOL = 1_000_000_000L;
    private static final int  SIGNATURE_FETCH_LIMIT = 50;

    private final SolanaService solanaService;

    /**
     * direction: "RECEIVED" when the watched address gained SOL,
     *            "SENT"     when the watched address lost SOL.
     */
    public record SolanaTx(
        String signature,
        String fromAddress,
        String toAddress,
        BigDecimal amount,
        long blockTime,
        String direction
    ) {}

    /**
     * Returns all finalized SOL transfers involving {@code address} that are
     * newer than {@code lastSignature} (exclusive), including both sends and receives.
     *
     * Signatures come back newest-first; we stop at {@code lastSignature} so
     * re-runs are O(new txs only).
     */
    public List<SolanaTx> getNewTransactions(String address, String lastSignature) {
        List<SolanaTx> result = new ArrayList<>();
        try {
            List<SignatureInformation> sigs = solanaService.getRpcClient()
                .getApi()
                .getSignaturesForAddress(new PublicKey(address), SIGNATURE_FETCH_LIMIT, Commitment.FINALIZED);

            for (SignatureInformation sigInfo : sigs) {
                if (sigInfo.getSignature().equals(lastSignature)) break;
                if (sigInfo.getErr() != null) continue;

                ConfirmedTransaction tx = solanaService.getRpcClient()
                    .getApi()
                    .getTransaction(sigInfo.getSignature());

                if (tx == null || tx.getMeta() == null || tx.getMeta().getErr() != null) continue;

                List<String> accounts = tx.getTransaction().getMessage().getAccountKeys();
                List<Long>   pre      = tx.getMeta().getPreBalances();
                List<Long>   post     = tx.getMeta().getPostBalances();

                int ourIdx = accounts.indexOf(address);
                if (ourIdx < 0) continue;

                long delta = post.get(ourIdx) - pre.get(ourIdx);
                if (delta == 0) continue;

                if (delta > 0) {
                    // RECEIVE: our address gained SOL
                    String sender = accounts.isEmpty() ? "unknown" : accounts.get(0);
                    BigDecimal amount = BigDecimal.valueOf(delta)
                        .divide(BigDecimal.valueOf(LAMPORTS_PER_SOL), 9, RoundingMode.HALF_UP);
                    result.add(new SolanaTx(sigInfo.getSignature(), sender, address, amount,
                        (long) sigInfo.getBlockTime(), "RECEIVED"));

                } else {
                    // SEND: our address lost SOL — find the primary recipient (first account with positive delta)
                    String recipient = "unknown";
                    long   received  = 0;
                    for (int i = 0; i < accounts.size(); i++) {
                        long d = post.get(i) - pre.get(i);
                        if (i != ourIdx && d > received) {
                            received  = d;
                            recipient = accounts.get(i);
                        }
                    }
                    // Amount sent = lamports received by recipient (excludes fee)
                    BigDecimal amount = BigDecimal.valueOf(received)
                        .divide(BigDecimal.valueOf(LAMPORTS_PER_SOL), 9, RoundingMode.HALF_UP);
                    result.add(new SolanaTx(sigInfo.getSignature(), address, recipient, amount,
                        (long) sigInfo.getBlockTime(), "SENT"));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get transactions for {}: {}", address, e.getMessage());
        }
        return result;
    }
}
