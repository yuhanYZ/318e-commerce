package com.ecommerce.productservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * REST client for the P2 call to Inventory Service.
 *
 * <p>Short timeouts on purpose: product details must stay responsive even when Inventory is
 * slow or down, because that call degrades to "no stock information" rather than failing
 * the request (domain-models §2.3.1, Inter-Service Note).
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate inventoryRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }
}
