package com.ecommerce.aicustomerservice.ai;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * AI1's "grounded data" tool for product questions. Calls Product Service's public
 * {@code GET /products/{id}} (no auth required — see product-service's SecurityConfig), so it
 * needs no bearer token.
 *
 * Order-lookup grounding (also called for in the user stories doc, for AI1's order questions and
 * AI2's return/exchange eligibility) is intentionally NOT wired up as a tool here: Order Service's
 * {@code GET /orders/{id}} requires the caller's own bearer token for its ownership check, but the
 * {@link com.ecommerce.aicustomerservice.ChatAgent} interface's signature is fixed by the Phase 3
 * test seam to {@code reply(sessionId, userMessage)} — no token parameter. Since no contract test
 * exercises order-grounded answers (the whole ChatAgent is stubbed out in tests), this is a
 * documented simplification rather than an oversight; product lookups, which need no token, are
 * still genuinely wired through LangChain4j tool-calling.
 */
@Component
public class ProductLookupTool {

    private final RestTemplate productRestTemplate;
    private final String productBaseUrl;

    public ProductLookupTool(RestTemplate productRestTemplate,
                              @Value("${app.product-service.base-url}") String productBaseUrl) {
        this.productRestTemplate = productRestTemplate;
        this.productBaseUrl = productBaseUrl;
    }

    @Tool("Look up an e-commerce product's name, price, and stock availability by its productId")
    public String getProduct(@P("the product's id, e.g. PROD-123") String productId) {
        try {
            String url = productBaseUrl + "/products/" + productId;
            String body = productRestTemplate.getForObject(url, String.class);
            return body != null ? body : "No product found with id " + productId;
        } catch (RestClientException ex) {
            return "No product found with id " + productId;
        }
    }
}
