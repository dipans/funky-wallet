package com.funkywallet.client.signing;

import lombok.Data;

@Data
public class KeyPairResponse {
    private String address;
    private String publicKey;
}
