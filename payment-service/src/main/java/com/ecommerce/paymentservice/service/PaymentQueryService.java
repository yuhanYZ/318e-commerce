package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.domain.OrderId;
import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.Refund;
import com.ecommerce.paymentservice.dto.PaymentPageResponse;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.exception.ApiException;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.repository.RefundRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query Application Service for Payment Service — specs/iter2/6-cqrs-command-query.md §3.2.
 * PAY2 (getPaymentByOrderId) / PAY4 (listAllPayments). Reads only.
 */
@Service
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    public PaymentQueryService(PaymentRepository paymentRepository, RefundRepository refundRepository) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
    }

    public PaymentResponse getPaymentByOrderId(String orderId) {
        Payment payment;
        try {
            payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(new OrderId(orderId))
                    .orElseThrow(PaymentQueryService::notFound);
        } catch (IllegalArgumentException ex) {
            throw notFound();
        }
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

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "No payment found for this order");
    }
}
