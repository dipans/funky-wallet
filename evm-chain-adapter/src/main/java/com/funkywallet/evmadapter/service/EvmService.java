package com.funkywallet.evmadapter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.core.methods.response.EthBlock.TransactionObject;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvmService {

    private final Web3j web3j;

    @Value("${geth.chain.id:1337}")
    private long chainId;

    public BigDecimal getBalance(String address) {
        try {
            EthGetBalance response = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
            BigInteger wei = response.getBalance();
            return Convert.fromWei(wei.toString(), Convert.Unit.ETHER);
        } catch (Exception e) {
            log.error("Failed to get balance for {}: {}", address, e.getMessage());
            throw new RuntimeException("Failed to get balance", e);
        }
    }

    public String buildUnsignedTx(String from, String to, BigDecimal amountEth) {
        try {
            BigInteger nonce = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.PENDING)
                .send().getTransactionCount();
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            BigInteger gasLimit = BigInteger.valueOf(21_000);
            BigInteger value = Convert.toWei(amountEth.toPlainString(), Convert.Unit.ETHER).toBigInteger();

            RawTransaction tx = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, to, value);
            byte[] encoded = TransactionEncoder.encode(tx);
            // store from+encoded as pipe-delimited so the signed payload round-trips
            return from + "|" + Numeric.toHexString(encoded);
        } catch (Exception e) {
            log.error("Failed to build unsigned tx: {}", e.getMessage());
            throw new RuntimeException("Failed to build unsigned tx", e);
        }
    }

    public String broadcast(String signedTxHex) {
        try {
            EthSendTransaction response = web3j.ethSendRawTransaction(signedTxHex).send();
            if (response.hasError()) {
                throw new RuntimeException("Broadcast error: " + response.getError().getMessage());
            }
            return response.getTransactionHash();
        } catch (Exception e) {
            log.error("Failed to broadcast tx: {}", e.getMessage());
            throw new RuntimeException("Failed to broadcast tx", e);
        }
    }

    public String getNodeInfo() {
        try {
            return web3j.web3ClientVersion().send().getWeb3ClientVersion();
        } catch (Exception e) {
            return "unavailable";
        }
    }

    public record BlockInfo(long blockNumber, String blockHash) {}

    public record TxInfo(String hash, String fromAddress, String toAddress, String value, String blockHash) {}

    public BlockInfo getLatestBlock() {
        try {
            EthBlock response = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send();
            EthBlock.Block block = response.getBlock();
            return new BlockInfo(block.getNumber().longValue(), block.getHash());
        } catch (Exception e) {
            log.error("Failed to get latest block: {}", e.getMessage());
            throw new RuntimeException("Failed to get latest block", e);
        }
    }

    public List<TxInfo> getBlockTransactions(long blockNumber) {
        try {
            EthBlock response = web3j.ethGetBlockByNumber(
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)), true).send();
            EthBlock.Block block = response.getBlock();
            if (block == null) {
                return new ArrayList<>();
            }
            String blockHash = block.getHash();
            List<TxInfo> result = new ArrayList<>();
            for (EthBlock.TransactionResult<?> txResult : block.getTransactions()) {
                TransactionObject tx = (TransactionObject) txResult.get();
                if (tx.getTo() == null) {
                    // skip contract creation transactions
                    continue;
                }
                String valueEth = Convert.fromWei(tx.getValue().toString(), Convert.Unit.ETHER).toPlainString();
                result.add(new TxInfo(tx.getHash(), tx.getFrom(), tx.getTo(), valueEth, blockHash));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to get transactions for block {}: {}", blockNumber, e.getMessage());
            throw new RuntimeException("Failed to get block transactions", e);
        }
    }
}
