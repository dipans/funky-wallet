package com.funkywallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funkywallet.model.entity.Network;
import com.funkywallet.model.request.CreateAccountRequest;
import com.funkywallet.model.response.AccountResponse;
import com.funkywallet.model.response.CreateAccountResponse;
import com.funkywallet.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@WithMockUser
class AccountControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AccountService accountService;

    @Test
    void createAccount_returns201WithMnemonic() throws Exception {
        AccountResponse accResp = new AccountResponse();
        accResp.setId(UUID.randomUUID());
        accResp.setAddress("0xDEAD");
        accResp.setPublicKey("pubkey");
        accResp.setNetwork(Network.ETHEREUM);
        accResp.setCreatedAt(Instant.now());

        when(accountService.createAccount(any(CreateAccountRequest.class)))
            .thenReturn(new CreateAccountResponse(accResp, "test mnemonic words"));

        CreateAccountRequest req = new CreateAccountRequest();
        req.setNetwork(Network.ETHEREUM);
        req.setChainId(560048);
        req.setChainName("Hoodi Testnet");
        req.setNetworkType("EVM");

        mockMvc.perform(post("/api/v1/accounts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mnemonic").value("test mnemonic words"))
            .andExpect(jsonPath("$.account.address").value("0xDEAD"));
    }
}
