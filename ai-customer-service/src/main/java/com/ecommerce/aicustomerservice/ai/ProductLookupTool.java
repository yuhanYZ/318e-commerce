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
 * needs no bearer token and can be a singleton.
 *
 * Order-lookup grounding is a separate tool, {@code OrderLookupTool}: Order Service's
 * {@code GET /orders/{id}} enforces ownership, so that tool has to carry the caller's bearer token
 * and is therefore built per call in {@code ai.ChatAgentConfig} from the token the
 * {@link com.ecommerce.aicustomerservice.ChatAgent} port receives
 * ({@code reply(sessionId, userMessage, bearerToken)}). That keeps the agent unable to read another
 * customer's orders even though it can look up any product. Verified end to end against a real
 * model: a product question is answered with the live price and stock, a question about the caller's
 * own order with its real status, and the same order question asked with another user's token with
 * "not found".
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
