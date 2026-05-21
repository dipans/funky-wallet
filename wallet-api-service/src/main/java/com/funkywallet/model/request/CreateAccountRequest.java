package com.funkywallet.model.request;

import com.funkywallet.model.entity.Network;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAccountRequest {
    @NotNull
    private Network network;

    @NotNull
    private Integer chainId;

    @NotBlank
    private String chainName;

    @NotBlank
    private String networkType; // EVM | SOLANA | BITCOIN
}
