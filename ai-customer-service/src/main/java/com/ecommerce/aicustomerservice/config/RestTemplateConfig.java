package com.ecommerce.aicustomerservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * No Phase 3 contract test binds MockRestServiceServer to any RestTemplate bean here — the whole
 * ChatAgent is replaced by a stub in tests (see AiCustomerServiceContractTests), bypassing this
 * entirely. Bean names below only need to be consistent with the other services' pattern, not any
 * specific test-required name.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate productRestTemplate() {
        return new RestTemplate();
    }
}
