package com.funkywallet.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateAccountResponse {
    private AccountResponse account;
    private String mnemonic;
}
