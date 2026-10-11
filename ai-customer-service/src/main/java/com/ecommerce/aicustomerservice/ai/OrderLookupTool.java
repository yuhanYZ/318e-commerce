package com.ecommerce.aicustomerservice.ai;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * AI1/AI2's order-grounding tool. Calls Order Service's {@code GET /orders/{id}}, forwarding the
 * end user's own bearer token — Order Service enforces order ownership (O2), so the agent can only
 * ever read the caller's own orders and cannot be talked into fetching someone else's.
 *
 * <p>Unlike {@link ProductLookupTool} (a singleton, since {@code GET /products/{id}} is public),
 * this tool is constructed per request because the token varies per caller: see
 * {@code ChatAgentConfig}, which builds the LangChain4j assistant per call with a token-bound
 * instance.
 */
public class OrderLookupTool {

    private final RestTemplate orderRestTemplate;
    private final String orderBaseUrl;
    private final String bearerToken;

    public OrderLookupTool(RestTemplate orderRestTemplate, String orderBaseUrl, String bearerToken) {
        this.orderRestTemplate = orderRestTemplate;
        this.orderBaseUrl = orderBaseUrl;
        this.bearerToken = bearerToken;
    }

    @Tool("Look up one of the customer's own orders (status, totals and line items) by its orderId, "
            + "e.g. ORD-1234ABCD")
    public String getOrder(@P("the order's id, e.g. ORD-1234ABCD") String orderId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (bearerToken != null) {
                headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
            }
            ResponseEntity<String> response = orderRestTemplate.exchange(
                    orderBaseUrl + "/orders/" + orderId, HttpMethod.GET, new HttpEntity<Void>(headers),
                    String.class);
            String body = response.getBody();
            return body != null ? body : "No order found with id " + orderId;
        } catch (RestClientException ex) {
            // Unknown id, or an order that belongs to someone else (Order Service answers 404/403)
            // — both are simply "no grounded data" as far as the assistant is concerned.
            return "No order found with id " + orderId;
        }
    }
}
