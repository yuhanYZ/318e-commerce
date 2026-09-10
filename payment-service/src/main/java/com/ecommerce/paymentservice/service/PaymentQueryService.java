package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.dto.PaymentPageResponse;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.exception.ApiException;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAY2 (getPaymentByOrderId) / PAY4 (listAllPayments). No ownership check on PAY2 — the contract
 * test has no "not owner" 403 scenario for payments (unlike Order Service's O2); PAY4's Admin-only
 * restriction is enforced entirely by SecurityConfig's static path rule.
 */
@Service
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;

    public PaymentQueryService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public PaymentResponse getPaymentByOrderId(String orderId) {
        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound",
                        "No payment found for this order"));
        return PaymentMapper.toResponse(payment);
    }

    public PaymentPageResponse listAllPayments(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Payment> result = paymentRepository.findAll(pageable);
        return new PaymentPageResponse(
                result.getContent().stream().map(PaymentMapper::toResponse).toList(),
                result.getNumber(),
                result.getTotalElements());
    }
}
