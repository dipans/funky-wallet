package com.funkywallet.controller;

import com.funkywallet.model.request.SendTransactionRequest;
import com.funkywallet.model.response.PagedResponse;
import com.funkywallet.model.response.TransactionResponse;
import com.funkywallet.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse sendTransaction(@Valid @RequestBody SendTransactionRequest request) {
        return transactionService.sendTransaction(request);
    }

    @GetMapping
    public PagedResponse<TransactionResponse> listTransactions(
            @RequestParam(required = false) String address,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return transactionService.listTransactions(address, page, size);
    }

    @GetMapping("/{id}")
    public TransactionResponse getTransaction(@PathVariable String id) {
        return transactionService.getTransaction(id);
    }

    @PatchMapping("/{id}/confirm")
    public TransactionResponse confirmTransaction(@PathVariable String id) {
        return transactionService.confirmTransaction(id);
    }
}
