package com.ecommerce.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Phase 3 test seam: the contract tests bind MockRestServiceServer to the bean named exactly
 * "orderRestTemplate" (see PaymentServiceContractTests). Plain RestTemplate, no rootUri —
 * PaymentCommandService builds the full URL from app.order-service.base-url.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate orderRestTemplate() {
        return new RestTemplate();
    }
}
