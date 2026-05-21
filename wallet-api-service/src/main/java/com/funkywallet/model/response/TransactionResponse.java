package com.funkywallet.model.response;

import com.funkywallet.model.entity.Network;
import com.funkywallet.model.entity.TransactionStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class TransactionResponse {
    private UUID id;
    private String hash;
    private String fromAddress;
    private String toAddress;
    private BigDecimal amount;
    private String symbol;
    private Network network;
    private TransactionStatus status;
    private Instant createdAt;
    private Instant confirmedAt;
}
