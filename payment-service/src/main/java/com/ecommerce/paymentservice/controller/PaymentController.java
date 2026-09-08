package com.ecommerce.paymentservice.controller;

import com.ecommerce.paymentservice.dto.request.PaymentRequest;
import com.ecommerce.paymentservice.dto.request.RefundRequest;
import com.ecommerce.paymentservice.dto.response.PaymentPageResponse;
import com.ecommerce.paymentservice.dto.response.PaymentResponse;
import com.ecommerce.paymentservice.dto.response.RefundResponse;
import com.ecommerce.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Presentation layer for the Payment Service. Performs basic request/response
 * plumbing only - no business logic - per specs/1-technical-architecture.md.
 * Endpoint shapes follow specs/4-api-endpoints.md and specs/openapi-contract.yaml.
 * Every @RequestParam/@PathVariable below is explicitly named, even though the
 * parent pom sets the -parameters compiler flag project-wide - defense in
 * depth against the bug that broke product-service's tests earlier.
 * <p>
 * PAY1-PAY3 also forward the caller's raw Authorization header value into
 * PaymentService, which passes it on to OrderClient - see OrderClient's
 * javadoc for why calls to Order Service need the caller's own token
 * (Authentication here only exposes the parsed principal, not the raw token
 * string, so the header is read directly).
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** PAY1. */
    @PostMapping
    public ResponseEntity<PaymentResponse> payForOrder(
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody PaymentRequest request) {
        String userId = (String) authentication.getPrincipal();
        PaymentResponse response = paymentService.payForOrder(userId, request, authorizationHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** PAY2. */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable(name = "orderId") String orderId) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(userId, orderId, authorizationHeader));
    }

    /** PAY3. */
    @PostMapping("/{id}/refund")
    public ResponseEntity<RefundResponse> requestRefund(
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable(name = "id") String id,
            @Valid @RequestBody RefundRequest request) {
        String userId = (String) authentication.getPrincipal();
        RefundResponse response = paymentService.requestRefund(userId, id, request, authorizationHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** PAY4. */
    @GetMapping
    public ResponseEntity<PaymentPageResponse> listAllPayments(
            @RequestParam(name = "page", defaultValue = "0") int page) {
        return ResponseEntity.ok(paymentService.listAllPayments(page));
    }
}
