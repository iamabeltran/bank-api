package com.portfolio.bankapi.transfer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.portfolio.bankapi.support.IntegrationTest;

/** Escenario completo por HTTP: crear cuentas, depositar y transferir con Idempotency-Key. */
@IntegrationTest
class TransferApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    private long createAccount(String owner) throws Exception {
        String body = mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"" + owner + "\",\"currency\":\"MXN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String transferBody(long from, long to, String amount) {
        return "{\"fromAccountId\":" + from + ",\"toAccountId\":" + to + ",\"amount\":" + amount + "}";
    }

    @Test
    void repeatingARequestWithTheSameKeyReturnsTheOriginalAndMovesMoneyOnce() throws Exception {
        long ana = createAccount("Ana");
        long luis = createAccount("Luis");
        mvc.perform(post("/accounts/{id}/deposits", ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500}"))
                .andExpect(status().isCreated());
        String key = UUID.randomUUID().toString();

        String firstBody = mvc.perform(post("/transfers").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(transferBody(ana, luis, "200.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long firstId = ((Number) JsonPath.read(firstBody, "$.id")).longValue();

        mvc.perform(post("/transfers").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(transferBody(ana, luis, "200.00")))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value((int) firstId));

        mvc.perform(get("/accounts/{id}", ana)).andExpect(jsonPath("$.balance").value(300.0));
        mvc.perform(get("/accounts/{id}", luis)).andExpect(jsonPath("$.balance").value(200.0));
    }

    @Test
    void reusingTheKeyWithADifferentAmountReturns422() throws Exception {
        long ana = createAccount("Ana");
        long luis = createAccount("Luis");
        mvc.perform(post("/accounts/{id}/deposits", ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500}"))
                .andExpect(status().isCreated());
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/transfers").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(transferBody(ana, luis, "200")))
                .andExpect(status().isCreated());

        mvc.perform(post("/transfers").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(transferBody(ana, luis, "999")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Idempotency key conflict"));
    }
}
