package com.funkywallet.controller;

import com.funkywallet.model.response.PagedResponse;
import com.funkywallet.model.response.TransactionResponse;
import com.funkywallet.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@WithMockUser
class TransactionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean TransactionService transactionService;

    @Test
    void listTransactions_returns200() throws Exception {
        PagedResponse<TransactionResponse> empty =
            new PagedResponse<>(List.of(), 0, 20, 0L, 0);
        when(transactionService.listTransactions(eq("0xABC"), eq(0), eq(20))).thenReturn(empty);

        mockMvc.perform(get("/api/v1/transactions").param("address", "0xABC"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(0));
    }
}
