package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.external.OrderInfo;
import com.ecommerce.paymentservice.exception.OrderServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Inter-service REST client for PAY1-PAY3: calls Order Service's
 * GET /orders/{id} (specs/openapi-contract.yaml) to check order existence,
 * ownership, status, and total amount. Unlike order-service's own
 * InventoryClient (which degrades gracefully because inventory-service didn't
 * exist yet during development), every one of Payment Service's checks against
 * Order Service is a correctness/security requirement - so an unreachable
 * Order Service always fails the request (503) rather than assuming a
 * default. A 404 from Order Service is returned as an empty Optional rather
 * than an exception, since what a "missing order" means differs by caller
 * (PAY1 turns it into 409 per the contract; PAY2/PAY3 have their own needs).
 */
@Component
@Slf4j
public class OrderClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OrderClient(
            RestTemplateBuilder builder,
            @Value("${app.services.order-service.base-url}") String baseUrl) {
        this.restTemplate = builder.build();
        this.baseUrl = baseUrl;
    }

    public Optional<OrderInfo> getOrder(String orderId) {
        try {
            OrderInfo response = restTemplate.getForObject(baseUrl + "/orders/{id}", OrderInfo.class, orderId);
            return Optional.ofNullable(response);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            log.error("Order Service unavailable while looking up orderId={}: {}", orderId, ex.getMessage());
            throw new OrderServiceUnavailableException("Could not reach Order Service for orderId=" + orderId, ex);
        }
    }
}
