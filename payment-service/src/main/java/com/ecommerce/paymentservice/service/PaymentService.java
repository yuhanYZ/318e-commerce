package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.PaymentStatus;
import com.ecommerce.paymentservice.domain.Refund;
import com.ecommerce.paymentservice.domain.RefundStatus;
import com.ecommerce.paymentservice.dto.external.OrderInfo;
import com.ecommerce.paymentservice.dto.external.OrderItemInfo;
import com.ecommerce.paymentservice.dto.request.PaymentRequest;
import com.ecommerce.paymentservice.dto.request.RefundRequest;
import com.ecommerce.paymentservice.dto.response.PaymentPageResponse;
import com.ecommerce.paymentservice.dto.response.PaymentResponse;
import com.ecommerce.paymentservice.dto.response.RefundResponse;
import com.ecommerce.paymentservice.event.OrderItemPayload;
import com.ecommerce.paymentservice.event.PaymentConfirmedEvent;
import com.ecommerce.paymentservice.event.PaymentFailedEvent;
import com.ecommerce.paymentservice.event.RefundProcessedEvent;
import com.ecommerce.paymentservice.exception.OrderAccessDeniedException;
import com.ecommerce.paymentservice.exception.OrderNotPayableException;
import com.ecommerce.paymentservice.exception.OrderServiceUnavailableException;
import com.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.ecommerce.paymentservice.exception.RefundNotEligibleException;
import com.ecommerce.paymentservice.messaging.PaymentEventPublisher;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Business logic for the Payment Service. Implements PAY1-PAY4 from
 * specs/2-user-stories.md, per the Service Layer responsibilities in
 * specs/1-technical-architecture.md (cross-service coordination with Order
 * Service, Kafka event publishing). There is no real or sandbox payment
 * gateway to integrate with for this project - {@link MockPaymentGateway}
 * simulates one deterministically (see its javadoc).
 * <p>
 * Interpretation gap: specs/2-user-stories.md's PAY3 scenario describes
 * refund eligibility as the order being linked to a "Cancelled/Returned"
 * status, but specs/3-domain-models.md's {@code OrderStatus} enum
 * (PENDING/PAID/PAYMENT_FAILED/SHIPPED/CANCELLED) has no RETURNED value -
 * eligibility here is therefore CANCELLED only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final String CANCELLED_STATUS = "CANCELLED";
    private static final String PENDING_STATUS = "PENDING";
    private static final int PAGE_SIZE = 20;

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderClient orderClient;
    private final MockPaymentGateway mockPaymentGateway;
    private final PaymentEventPublisher paymentEventPublisher;

    /**
     * PAY1: pay for a pending order. Validates ownership and payability via
     * Order Service, cross-checks the requested amount against the order's
     * actual total (a mismatch is treated as a declined payment, not a 400 -
     * the contract only reserves 409 for "order not found or already paid"),
     * then runs it through the mock gateway. Always returns 201 with the
     * resulting Payment (SUCCESS or FAILED) - see OrderNotPayableException's
     * javadoc for the one case that's a hard 409 instead.
     */
    @Transactional
    public PaymentResponse payForOrder(String userId, PaymentRequest request) {
        OrderInfo order = orderClient.getOrder(request.getOrderId())
                .orElseThrow(() -> new OrderNotPayableException("Order not found: " + request.getOrderId()));

        if (!order.getUserId().equals(userId)) {
            throw new OrderAccessDeniedException("Order " + request.getOrderId() + " does not belong to the current user");
        }
        if (!PENDING_STATUS.equals(order.getStatus())) {
            throw new OrderNotPayableException(
                    "Order " + request.getOrderId() + " is not payable from status " + order.getStatus());
        }

        boolean amountMatches = request.getAmount().compareTo(order.getTotalAmount()) == 0;
        boolean gatewayAuthorized = mockPaymentGateway.authorize(request.getMethod());
        boolean success = amountMatches && gatewayAuthorized;

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .amount(request.getAmount())
                .status(success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED)
                .method(request.getMethod())
                .build();
        payment = paymentRepository.save(payment);

        if (success) {
            paymentEventPublisher.publishPaymentConfirmed(new PaymentConfirmedEvent(order.getId()));
        } else {
            String reason = !amountMatches
                    ? "Requested amount " + request.getAmount() + " does not match order total " + order.getTotalAmount()
                    : "Payment declined by gateway for method=" + request.getMethod();
            log.warn("Payment failed for orderId={}: {}", order.getId(), reason);
            paymentEventPublisher.publishPaymentFailed(
                    new PaymentFailedEvent(order.getId(), reason, toEventItems(order.getItems())));
        }

        return toResponse(payment);
    }

    /** PAY2: current payment status for an order, enforcing ownership via Order Service. */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(String userId, String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException("No payment found for orderId=" + orderId));

        OrderInfo order = orderClient.getOrder(orderId)
                .orElseThrow(() -> new OrderServiceUnavailableException(
                        "Order " + orderId + " referenced by an existing payment could not be found via Order Service"));
        if (!order.getUserId().equals(userId)) {
            throw new OrderAccessDeniedException("Order " + orderId + " does not belong to the current user");
        }

        return toResponse(payment);
    }

    /**
     * PAY3: request a refund against a specific payment. Every attempt is
     * persisted (PROCESSED or REJECTED) before this method returns or throws -
     * see Refund's javadoc for why - so PAY4's transaction history stays a
     * complete audit trail even for ineligible attempts.
     */
    @Transactional
    public RefundResponse requestRefund(String userId, String paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        OrderInfo order = orderClient.getOrder(payment.getOrderId()).orElse(null);
        if (order == null) {
            log.error("Payment {} references orderId={} which Order Service could not find", paymentId, payment.getOrderId());
        } else if (!order.getUserId().equals(userId)) {
            throw new OrderAccessDeniedException("Order " + payment.getOrderId() + " does not belong to the current user");
        }

        String ineligibleReason = ineligibilityReason(payment, order, request.getAmount());
        boolean eligible = ineligibleReason == null;

        LocalDateTime now = LocalDateTime.now();
        Refund refund = Refund.builder()
                .paymentId(paymentId)
                .amount(request.getAmount())
                .status(eligible ? RefundStatus.PROCESSED : RefundStatus.REJECTED)
                .requestedAt(now)
                .processedAt(now)
                .build();
        refund = refundRepository.save(refund);

        if (!eligible) {
            throw new RefundNotEligibleException(ineligibleReason);
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        paymentEventPublisher.publishRefundProcessed(
                new RefundProcessedEvent(payment.getOrderId(), paymentId, refund.getId(), request.getAmount()));

        return toResponse(refund);
    }

    /** PAY4 (Admin): all payment transactions, newest first. */
    @Transactional(readOnly = true)
    public PaymentPageResponse listAllPayments(int page) {
        Page<Payment> result = paymentRepository.findAll(
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<PaymentResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return new PaymentPageResponse(content, result.getNumber(), result.getTotalElements());
    }

    /** @return null if eligible, otherwise the reason it isn't. */
    private String ineligibilityReason(Payment payment, OrderInfo order, BigDecimal requestedAmount) {
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            return "Payment " + payment.getId() + " is not eligible for refund from status " + payment.getStatus();
        }
        if (order == null || !CANCELLED_STATUS.equals(order.getStatus())) {
            return "Order " + payment.getOrderId() + " is not in a refund-eligible status";
        }
        if (requestedAmount.compareTo(payment.getAmount()) > 0) {
            return "Refund amount " + requestedAmount + " exceeds the paid amount " + payment.getAmount();
        }
        return null;
    }

    private List<OrderItemPayload> toEventItems(List<OrderItemInfo> items) {
        return items.stream()
                .map(i -> new OrderItemPayload(i.getProductId(), i.getQuantity()))
                .toList();
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getStatus().name(), payment.getMethod(), payment.getCreatedAt());
    }

    private RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
                refund.getId(), refund.getPaymentId(), refund.getAmount(),
                refund.getStatus().name(), refund.getRequestedAt());
    }
}
