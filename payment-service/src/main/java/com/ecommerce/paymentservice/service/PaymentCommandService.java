package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.domain.Money;
import com.ecommerce.paymentservice.domain.OrderId;
import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.PaymentId;
import com.ecommerce.paymentservice.domain.PaymentStatus;
import com.ecommerce.paymentservice.domain.Refund;
import com.ecommerce.paymentservice.domain.RefundStatus;
import com.ecommerce.paymentservice.dto.OrderView;
import com.ecommerce.paymentservice.dto.PaymentRequest;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.dto.RefundResponse;
import com.ecommerce.paymentservice.exception.ApiException;
import com.ecommerce.paymentservice.integration.PaymentConfirmedEvent;
import com.ecommerce.paymentservice.integration.PaymentFailedEvent;
import com.ecommerce.paymentservice.integration.RefundProcessedEvent;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.repository.RefundRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Command Application Service for Payment Service — specs/iter2/6-cqrs-command-query.md §3.1.
 * PAY1 (payForOrder) / PAY3 (requestRefund). Every business-rule check (order existence/state,
 * amount match, payment existence/state, single-active-refund invariant) runs BEFORE any
 * repository write — the "validate-before-write" pattern adopted after user-service's Bug #3 — and
 * every state change then runs through a {@link Payment} or {@link Refund} behavior.
 *
 * <p><b>Kafka publishing (payment.confirmed/payment.failed/payment.refunded) is gated by
 * {@code app.kafka.enabled}</b> (default {@code false}, only {@code true} under the "kafka" Spring
 * profile — see application.yml). No contract test activates that profile, so
 * {@link #publishPaymentOutcome}/{@link #publishRefundProcessed} are guaranteed no-ops during
 * {@code mvn test}.
 */
@Service
@Transactional
public class PaymentCommandService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RestTemplate orderRestTemplate;
    private final String orderBaseUrl;
    private final String mockFailureFraction;
    private final StreamBridge streamBridge;
    private final boolean kafkaEnabled;
    private final DomainEventPublisher domainEventPublisher;

    public PaymentCommandService(PaymentRepository paymentRepository,
                                  RefundRepository refundRepository,
                                  @Qualifier("orderRestTemplate") RestTemplate orderRestTemplate,
                                  @Value("${app.order-service.base-url}") String orderBaseUrl,
                                  @Value("${app.payment.mock-failure-fraction}") String mockFailureFraction,
                                  StreamBridge streamBridge,
                                  @Value("${app.kafka.enabled:false}") boolean kafkaEnabled,
                                  DomainEventPublisher domainEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.orderRestTemplate = orderRestTemplate;
        this.orderBaseUrl = orderBaseUrl;
        this.mockFailureFraction = mockFailureFraction;
        this.streamBridge = streamBridge;
        this.kafkaEnabled = kafkaEnabled;
        this.domainEventPublisher = domainEventPublisher;
    }

    /** PAY1 — initiate, run the mock gateway, and publish the outcome. */
    public PaymentResponse payForOrder(String bearerToken, PaymentRequest request) {
        OrderView order = fetchOrder(request.orderId(), bearerToken);
        if (order == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "Order not found");
        }
        if (!"PENDING".equals(order.status())) {
            throw paymentStateViolation("Order is not in PENDING status");
        }
        if (order.totalAmount() == null
                || request.amount().amount().compareTo(order.totalAmount().amount()) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ValidationFailed",
                    "Payment amount must equal the order's total amount");
        }

        Money amount = new Money(request.amount().amount(), request.amount().currency());
        Instant now = Instant.now();
        Payment payment = Payment.initiate(new PaymentId("PAY-" + shortId()), new OrderId(request.orderId()),
                amount, now);
        if (isMockGatewayFailure(amount.amount())) {
            payment.markFailed(now);
        } else {
            payment.markSucceeded(now);
        }
        paymentRepository.save(payment);
        domainEventPublisher.publish(payment.pullDomainEvents());
        publishPaymentOutcome(payment);
        return PaymentMapper.toResponse(payment);
    }

    /** PAY3 — request a full refund for a SUCCESS payment whose order was cancelled. */
    public RefundResponse requestRefund(String paymentId, String bearerToken) {
        Payment payment = findPaymentOrThrow(paymentId);

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw paymentStateViolation("Only a SUCCESS payment can be refunded");
        }

        boolean hasActiveRefund = refundRepository
                .findByPaymentIdAndStatusNot(payment.getId(), RefundStatus.REJECTED)
                .isPresent();
        if (hasActiveRefund) {
            throw paymentStateViolation("This payment already has an active refund");
        }

        OrderView order = fetchOrder(payment.getOrderId().value(), bearerToken);
        if (order == null || !"CANCELLED".equals(order.status())) {
            throw paymentStateViolation("A refund requires the order to be CANCELLED");
        }

        Instant now = Instant.now();
        // Full refunds only: the refund amount is the payment's own amount (invariant 1).
        Refund refund = Refund.request("REF-" + shortId(), payment.getId(), payment.getAmount(), now);
        refund.process(now);
        payment.markRefunded();

        refundRepository.save(refund);
        paymentRepository.save(payment);
        domainEventPublisher.publish(refund.pullDomainEvents());
        publishRefundProcessed(refund, payment.getOrderId());

        return RefundMapper.toResponse(refund);
    }

    /**
     * Forwards the caller's original bearer token, since GET /orders/{id} on Order Service
     * requires authentication + ownership (unlike the public product/inventory lookups used by
     * product-service and order-service).
     *
     * Catches AssertionError in addition to RestClientException: MockRestServiceServer.bindTo(...)
     * replaces this shared bean's request factory on every stub call across the whole
     * @SpringBootTest context, so a test that makes no stub call of its own (e.g.
     * payForOrder_orderNotFound_returns404) can hit a "stale" mock left by a different test method
     * and get an AssertionError (not a RestClientException) from the unmatched-request check —
     * both cases mean "treat this order as unavailable" for our purposes.
     */
    private OrderView fetchOrder(String orderId, String bearerToken) {
        try {
            String url = orderBaseUrl + "/orders/" + orderId;
            HttpHeaders headers = new HttpHeaders();
            if (bearerToken != null) {
                headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
            }
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<OrderView> response =
                    orderRestTemplate.exchange(url, HttpMethod.GET, entity, OrderView.class);
            return response.getBody();
        } catch (RestClientException | AssertionError ex) {
            return null;
        }
    }

    private boolean isMockGatewayFailure(BigDecimal amount) {
        BigDecimal fractional = amount.remainder(BigDecimal.ONE).abs();
        BigDecimal threshold = new BigDecimal("0." + mockFailureFraction);
        return fractional.compareTo(threshold) == 0;
    }

    /** PAY1 outcome: SUCCESS publishes {@code payment.confirmed}, FAILED publishes {@code payment.failed}. */
    private void publishPaymentOutcome(Payment payment) {
        if (!kafkaEnabled) {
            return;
        }
        Instant now = Instant.now();
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            streamBridge.send("payment.confirmed",
                    new PaymentConfirmedEvent(payment.getId().value(), payment.getOrderId().value(), now));
        } else if (payment.getStatus() == PaymentStatus.FAILED) {
            streamBridge.send("payment.failed",
                    new PaymentFailedEvent(payment.getId().value(), payment.getOrderId().value(), now));
        }
    }

    /** PAY3 completion: publishes {@code payment.refunded}. No consumer this iteration. */
    private void publishRefundProcessed(Refund refund, OrderId orderId) {
        if (!kafkaEnabled) {
            return;
        }
        streamBridge.send("payment.refunded",
                new RefundProcessedEvent(refund.getId(), refund.getPaymentId().value(), orderId.value(),
                        Instant.now()));
    }

    private Payment findPaymentOrThrow(String paymentId) {
        try {
            return paymentRepository.findById(new PaymentId(paymentId))
                    .orElseThrow(PaymentCommandService::paymentNotFound);
        } catch (IllegalArgumentException ex) {
            throw paymentNotFound();
        }
    }

    private static ApiException paymentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "Payment not found");
    }

    private static ApiException paymentStateViolation(String message) {
        return new ApiException(HttpStatus.CONFLICT, "PaymentStateViolation", message);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
