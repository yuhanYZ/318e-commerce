package com.ecommerce.paymentservice.repository;

import com.ecommerce.paymentservice.domain.Refund;
import com.ecommerce.paymentservice.domain.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, String> {

    /** "At most one active (non-REJECTED) Refund per Payment" (invariant 2). */
    Optional<Refund> findByPaymentIdAndStatusNot(String paymentId, RefundStatus status);
}
