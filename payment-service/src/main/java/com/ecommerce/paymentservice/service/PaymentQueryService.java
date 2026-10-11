package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.domain.OrderId;
import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.Refund;
import com.ecommerce.paymentservice.dto.PaymentPageResponse;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.exception.ApiException;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.repository.RefundRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Query Application Service for Payment Service — specs/iter2/6-cqrs-command-query.md §3.2.
 * PAY2 (getPaymentByOrderId) / PAY4 (listAllPayments). Reads only.
 */
@Service
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RestTemplate orderRestTemplate;
    private final String orderBaseUrl;

    public PaymentQueryService(PaymentRepository paymentRepository,
                                RefundRepository refundRepository,
                                @Qualifier("orderRestTemplate") RestTemplate orderRestTemplate,
                                @Value("${app.order-service.base-url}") String orderBaseUrl) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.orderRestTemplate = orderRestTemplate;
        this.orderBaseUrl = orderBaseUrl;
    }

    /** PAY2 — the caller must be able to read the payment's order; see {@link #verifyCallerOwnsOrder}. */
    public PaymentResponse getPaymentByOrderId(String orderId, String bearerToken) {
        Payment payment = findPaymentOrThrow(orderId);
        verifyCallerOwnsOrder(payment.getOrderId().value(), bearerToken);
        return PaymentMapper.toResponse(payment);
    }

    /**
     * PAY4 — returns both record sets the user story promises ("all payment and refund records
     * for reconciliation"): the Payment page in {@code content}, and an independently-paged Refund
     * slice (same page/size parameters, newest first) in {@code refunds}.
     */
    public PaymentPageResponse listAllPayments(int page, int size) {
        PageRequest paymentPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Payment> payments = paymentRepository.findAll(paymentPageable);

        PageRequest refundPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt"));
        Page<Refund> refunds = refundRepository.findAll(refundPageable);

        return new PaymentPageResponse(
                payments.getContent().stream().map(PaymentMapper::toResponse).toList(),
                payments.getNumber(),
                payments.getTotalElements(),
                refunds.getContent().stream().map(RefundMapper::toResponse).toList(),
                refunds.getTotalElements());
    }

    /**
     * PAY2 must not let one customer read another customer's payment record. Payment itself does
     * not store the buyer, so ownership is settled where it is actually authoritative — Order
     * Service's {@code GET /orders/{id}}, called with the caller's own bearer token, the same
     * forwarded-token pattern PAY1/PAY3 already use (4-api-endpoints-v2.md §7a), which is what makes
     * Order Service's ownership check apply to the end user.
     *
     * <p>Any non-2xx outcome — 403 for someone else's order, 404 for an unknown one, or an
     * unreachable Order Service — is reported as 403, so this endpoint never reveals whether a
     * payment exists to a caller who does not own the order.
     *
     * <p>AssertionError is caught alongside RestClientException for the same reason documented on
     * {@code PaymentCommandService#fetchOrder}: MockRestServiceServer swaps the shared template's
     * request factory per test method, so an unmatched request surfaces as an AssertionError rather
     * than a RestClientException.
     */
    private void verifyCallerOwnsOrder(String orderId, String bearerToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (bearerToken != null) {
                headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
            }
            orderRestTemplate.exchange(orderBaseUrl + "/orders/" + orderId, HttpMethod.GET,
                    new HttpEntity<Void>(headers), String.class);
        } catch (RestClientException | AssertionError ex) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "This payment does not belong to you");
        }
    }

    private Payment findPaymentOrThrow(String orderId) {
        try {
            return paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(new OrderId(orderId))
                    .orElseThrow(PaymentQueryService::notFound);
        } catch (IllegalArgumentException ex) {
            throw notFound();
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "No payment found for this order");
    }
}
