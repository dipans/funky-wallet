package com.funkywallet.service;

import com.funkywallet.client.chain.ChainAdapterClient;
import com.funkywallet.model.entity.BlockSyncState;
import com.funkywallet.model.entity.Network;
import com.funkywallet.model.entity.SolanaSyncState;
import com.funkywallet.model.entity.Transaction;
import com.funkywallet.model.entity.TransactionStatus;
import com.funkywallet.repository.AccountRepository;
import com.funkywallet.repository.BlockSyncStateRepository;
import com.funkywallet.repository.SolanaSyncStateRepository;
import com.funkywallet.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockWatcherService {

    private final ChainAdapterClient chainClient;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BlockSyncStateRepository blockSyncStateRepository;
    private final SolanaSyncStateRepository solanaSyncStateRepository;

    @Scheduled(fixedDelayString = "${blockwatcher.interval-ms:15000}")
    @Transactional
    public void watchBlocks() {
        try {
            // 1. Get current user addresses (all accounts)
            Set<String> watchedAddresses = accountRepository.findAll()
                .stream().map(a -> a.getAddress().toLowerCase()).collect(Collectors.toSet());
            if (watchedAddresses.isEmpty()) return;

            // 2. Get latest block
            ChainAdapterClient.BlockInfo latest = chainClient.getLatestBlock();

            // 3. Load or init sync state for ETHEREUM
            BlockSyncState state = blockSyncStateRepository.findByNetwork("ETHEREUM")
                .orElseGet(() -> {
                    BlockSyncState s = new BlockSyncState();
                    s.setNetwork("ETHEREUM");
                    s.setLastProcessedBlock(Math.max(0, latest.blockNumber() - 1));
                    return s;
                });

            long fromBlock = state.getLastProcessedBlock() + 1;
            long toBlock = latest.blockNumber();
            if (fromBlock > toBlock) return;

            // 4. Process each new block — record both receives and sends
            for (long blockNum = fromBlock; blockNum <= toBlock; blockNum++) {
                List<ChainAdapterClient.TxInfo> txs = chainClient.getBlockTransactions(blockNum);
                for (ChainAdapterClient.TxInfo tx : txs) {
                    boolean isReceive = tx.toAddress() != null
                        && watchedAddresses.contains(tx.toAddress().toLowerCase());
                    boolean isSend = tx.fromAddress() != null
                        && watchedAddresses.contains(tx.fromAddress().toLowerCase());

                    if (isReceive && !transactionRepository.existsByHashAndToAddressAndStatus(tx.hash(), tx.toAddress(), TransactionStatus.RECEIVED)) {
                        Transaction record = buildEvmTx(tx, TransactionStatus.RECEIVED);
                        transactionRepository.save(record);
                        log.info("Recorded EVM receive {} to {}", tx.hash(), tx.toAddress());
                    }
                    if (isSend && !transactionRepository.existsByHashAndFromAddressAndStatus(tx.hash(), tx.fromAddress(), TransactionStatus.CONFIRMED)) {
                        Transaction record = buildEvmTx(tx, TransactionStatus.CONFIRMED);
                        transactionRepository.save(record);
                        log.info("Recorded EVM send {} from {}", tx.hash(), tx.fromAddress());
                    }
                }
                state.setLastProcessedBlock(blockNum);
                state.setLastProcessedBlockHash(latest.blockHash());
            }

            blockSyncStateRepository.save(state);
        } catch (Exception e) {
            log.warn("Block watcher error: {}", e.getMessage());
        }
    }

    private Transaction buildEvmTx(ChainAdapterClient.TxInfo tx, TransactionStatus status) {
        Transaction t = new Transaction();
        t.setHash(tx.hash());
        t.setFromAddress(tx.fromAddress());
        t.setToAddress(tx.toAddress());
        t.setAmount(new BigDecimal(tx.value()));
        t.setSymbol("ETH");
        t.setNetwork(Network.ETHEREUM);
        t.setStatus(status);
        t.setBlockHash(tx.blockHash());
        t.setConfirmedAt(Instant.now());
        return t;
    }

    /**
     * Polls each Solana account for incoming SOL transfers using getSignaturesForAddress.
     * Runs on the same interval as the EVM watcher.
     *
     * Unlike EVM (block iteration), Solana tracks the last seen signature per address.
     * New signatures are fetched newest-first and we stop at the last known one.
     */
    @Scheduled(fixedDelayString = "${blockwatcher.interval-ms:15000}")
    @Transactional
    public void watchSolanaAccounts() {
        try {
            List<String> solanaAddresses = accountRepository.findAll()
                .stream()
                .filter(a -> "SOLANA".equals(a.getNetworkType()))
                .map(com.funkywallet.model.entity.Account::getAddress)
                .toList();

            if (solanaAddresses.isEmpty()) return;

            for (String address : solanaAddresses) {
                // Load or init per-address sync state
                SolanaSyncState state = solanaSyncStateRepository.findById(address)
                    .orElseGet(() -> {
                        SolanaSyncState s = new SolanaSyncState();
                        s.setAddress(address);
                        return s;
                    });

                List<ChainAdapterClient.SolanaIncomingTx> txs =
                    chainClient.getSolanaNewTransactions(address, state.getLastSignature());

                for (ChainAdapterClient.SolanaIncomingTx tx : txs) {
                    boolean isSent = "SENT".equals(tx.direction());
                    // Status-aware dedup: the CONFIRMED (sent) record has toAddress=recipient,
                    // which would match an existsByHashAndToAddress check for the incoming RECEIVED
                    // record — so we must also check status to avoid blocking self-send receives.
                    if (isSent && transactionRepository.existsByHashAndFromAddressAndStatus(tx.signature(), tx.fromAddress(), TransactionStatus.CONFIRMED)) continue;
                    if (!isSent && transactionRepository.existsByHashAndToAddressAndStatus(tx.signature(), tx.toAddress(), TransactionStatus.RECEIVED)) continue;

                    Transaction record = new Transaction();
                    record.setHash(tx.signature());
                    record.setFromAddress(tx.fromAddress());
                    record.setToAddress(tx.toAddress());
                    record.setAmount(tx.amount());
                    record.setSymbol("SOL");
                    record.setNetwork(Network.SOLANA);
                    record.setStatus(isSent ? TransactionStatus.CONFIRMED : TransactionStatus.RECEIVED);
                    record.setConfirmedAt(Instant.ofEpochSecond(tx.blockTime()));
                    transactionRepository.save(record);
                    log.info("Recorded Solana {} {} {}", tx.direction(), tx.signature(), address);
                }

                // Update last signature to the newest one seen (first in the list — newest-first order)
                if (!txs.isEmpty()) {
                    state.setLastSignature(txs.get(0).signature());
                    solanaSyncStateRepository.save(state);
                } else if (state.getLastSignature() == null) {
                    // First run, no transactions yet — still persist the state row
                    solanaSyncStateRepository.save(state);
                }
            }
        } catch (Exception e) {
            log.warn("Solana watcher error: {}", e.getMessage());
        }
    }
}
