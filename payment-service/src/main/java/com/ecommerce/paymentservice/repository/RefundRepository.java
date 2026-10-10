package com.ecommerce.paymentservice.repository;

import com.ecommerce.paymentservice.domain.PaymentId;
import com.ecommerce.paymentservice.domain.Refund;
import com.ecommerce.paymentservice.domain.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, String> {

    Optional<Refund> findByPaymentIdAndStatusNot(PaymentId paymentId, RefundStatus status);
}
