package com.funkywallet.model.response;

import com.funkywallet.model.entity.Network;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class BalanceResponse {
    private String address;
    private Network network;
    private String amount;
    private String symbol;
    private Instant updatedAt;
}
