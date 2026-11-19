package com.portfolio.bankapi.transaction;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.portfolio.bankapi.error.AccountNotFoundException;
import com.portfolio.bankapi.error.InvalidPaginationException;

@WebMvcTest(TransactionHistoryController.class)
class TransactionHistoryControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    TransactionHistoryService service;

    @Test
    void defaultsToPageZeroAndSize20() throws Exception {
        given(service.history(1L, 0, 20)).willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/accounts/1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void passesPageAndSizeToTheService() throws Exception {
        given(service.history(1L, 3, 5)).willReturn(new PageResponse<>(List.of(), 3, 5, 40, 8));

        mvc.perform(get("/accounts/1/transactions").param("page", "3").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.totalPages").value(8));
    }

    @Test
    void invalidPaginationReturns400() throws Exception {
        given(service.history(1L, 0, 500)).willThrow(new InvalidPaginationException("size must be between 1 and 100"));

        mvc.perform(get("/accounts/1/transactions").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid pagination"));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        given(service.history(99L, 0, 20)).willThrow(new AccountNotFoundException(99L));

        mvc.perform(get("/accounts/99/transactions"))
                .andExpect(status().isNotFound());
    }
}
