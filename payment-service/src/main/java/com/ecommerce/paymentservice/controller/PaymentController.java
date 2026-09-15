package com.ecommerce.paymentservice.controller;

import com.ecommerce.paymentservice.dto.PaymentPageResponse;
import com.ecommerce.paymentservice.dto.PaymentRequest;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.dto.RefundResponse;
import com.ecommerce.paymentservice.service.PaymentCommandService;
import com.ecommerce.paymentservice.service.PaymentQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * specs/iter2/openapi-contract-v2.yaml paths /payments/*. PAY1/PAY3 forward the caller's raw
 * Authorization header to Order Service (its GET /orders/{id} requires auth+ownership), so both
 * endpoints capture it via @RequestHeader rather than only using Authentication for the userId.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentCommandService commandService;
    private final PaymentQueryService queryService;

    public PaymentController(PaymentCommandService commandService, PaymentQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse payForOrder(@Valid @RequestBody PaymentRequest request,
                                        @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return commandService.payForOrder(authorizationHeader, request);
    }

    @GetMapping("/order/{orderId}")
    public PaymentResponse getPaymentByOrderId(@PathVariable String orderId) {
        return queryService.getPaymentByOrderId(orderId);
    }

    @PostMapping("/{id}/refund")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse requestRefund(@PathVariable String id,
                                         @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return commandService.requestRefund(id, authorizationHeader);
    }

    @GetMapping
    public PaymentPageResponse listAllPayments(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return queryService.listAllPayments(page, size);
    }
}
