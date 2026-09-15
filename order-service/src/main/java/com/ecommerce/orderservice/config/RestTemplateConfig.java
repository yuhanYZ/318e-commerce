package com.ecommerce.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Phase 3 test seam: the contract tests bind MockRestServiceServer to two beans named exactly
 * "productRestTemplate" and "inventoryRestTemplate" (see OrderServiceContractTests). Plain
 * RestTemplate instances, no rootUri — OrderCommandService builds each full URL explicitly from
 * the corresponding app.*.base-url property.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate productRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RestTemplate inventoryRestTemplate() {
        return new RestTemplate();
    }
}
