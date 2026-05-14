package com.funkywallet.solanaadapter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
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

    private final SolanaService solanaService; // provides rpcClient via getter

    public record IncomingTx(
        String signature,
        String fromAddress,
        String toAddress,
        BigDecimal amount,
        long blockTime
    ) {}

    /**
     * Returns finalized transactions where {@code address} received SOL,
     * newer than {@code lastSignature} (exclusive).
     *
     * Signatures come back newest-first from Solana RPC. We stop iteration
     * as soon as we encounter {@code lastSignature}, so re-runs are efficient.
     *
     * @param address       wallet address to watch
     * @param lastSignature last signature already processed (null = first run)
     */
    public List<IncomingTx> getNewIncomingTransactions(String address, String lastSignature) {
        List<IncomingTx> result = new ArrayList<>();
        try {
            List<SignatureInformation> sigs = solanaService.getRpcClient()
                .getApi()
                .getSignaturesForAddress(new PublicKey(address), SIGNATURE_FETCH_LIMIT, Commitment.FINALIZED);

            for (SignatureInformation sigInfo : sigs) {
                // Stop when we reach the last already-processed signature
                if (sigInfo.getSignature().equals(lastSignature)) break;
                // Skip on-chain failures
                if (sigInfo.getErr() != null) continue;

                ConfirmedTransaction tx = solanaService.getRpcClient()
                    .getApi()
                    .getTransaction(sigInfo.getSignature());

                if (tx == null || tx.getMeta() == null || tx.getMeta().getErr() != null) continue;

                List<String> accounts  = tx.getTransaction().getMessage().getAccountKeys();
                List<Long>   preBals   = tx.getMeta().getPreBalances();
                List<Long>   postBals  = tx.getMeta().getPostBalances();

                int ourIdx = accounts.indexOf(address);
                if (ourIdx < 0) continue;

                long delta = postBals.get(ourIdx) - preBals.get(ourIdx);
                if (delta <= 0) continue; // Not a receive for our address

                // Sender = account[0] (fee payer), which is typically the initiating wallet
                String fromAddress = accounts.isEmpty() ? "unknown" : accounts.get(0);

                BigDecimal amount = BigDecimal.valueOf(delta)
                    .divide(BigDecimal.valueOf(LAMPORTS_PER_SOL), 9, RoundingMode.HALF_UP);

                result.add(new IncomingTx(
                    sigInfo.getSignature(),
                    fromAddress,
                    address,
                    amount,
                    (long) sigInfo.getBlockTime()
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to get new transactions for {}: {}", address, e.getMessage());
        }
        return result;
    }
}
