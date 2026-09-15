package com.ecommerce.productservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Phase 3 test seam: the Phase 3 contract tests bind {@code MockRestServiceServer} to a bean
 * named exactly "inventoryRestTemplate" (see ProductServiceContractTests). Plain RestTemplate,
 * no rootUri — the base URL is applied explicitly in ProductQueryService so the outgoing request
 * URI is unambiguous for both MockRestServiceServer and real Inventory Service calls.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate inventoryRestTemplate() {
        return new RestTemplate();
    }
}
