package com.funkywallet.model.request;

import com.funkywallet.model.entity.Network;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SendTransactionRequest {

    @NotBlank
    private String fromAddress;

    @NotBlank
    private String toAddress;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    private Network network;
}
