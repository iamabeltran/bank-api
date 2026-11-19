package com.portfolio.bankapi.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.portfolio.bankapi.support.IntegrationTest;

@IntegrationTest
class OpenApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void theOpenApiSpecificationListsTheMainEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("bank-api"))
                .andExpect(jsonPath("$.paths['/transfers']").exists())
                .andExpect(jsonPath("$.paths['/accounts/{id}/transactions']").exists());
    }

    @Test
    void theHealthEndpointReportsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
