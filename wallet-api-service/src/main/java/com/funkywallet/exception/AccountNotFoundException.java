package com.funkywallet.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String address) {
        super("Account not found: " + address);
    }
}
