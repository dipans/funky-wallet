package com.funkywallet.controller;

import com.funkywallet.model.request.CreateAccountRequest;
import com.funkywallet.model.response.AccountResponse;
import com.funkywallet.model.response.BalanceResponse;
import com.funkywallet.model.response.CreateAccountResponse;
import com.funkywallet.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateAccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request);
    }

    @GetMapping
    public List<AccountResponse> listAccounts() {
        return accountService.listAccounts();
    }

    @GetMapping("/{address}")
    public AccountResponse getAccount(@PathVariable String address) {
        return accountService.getAccount(address);
    }

    @GetMapping("/{address}/balance")
    public BalanceResponse getBalance(@PathVariable String address) {
        return accountService.getBalance(address);
    }
}
