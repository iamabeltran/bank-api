package com.portfolio.bankapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI bankApiOpenApi() {
        return new OpenAPI().info(new Info()
                .title("bank-api")
                .version("0.1.0")
                .description("Transactional banking REST API: accounts, deposits, idempotent transfers "
                        + "and paginated movement history. Demo project: it has no authentication."));
    }
}
