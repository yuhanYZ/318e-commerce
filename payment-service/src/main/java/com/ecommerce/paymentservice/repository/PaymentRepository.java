package com.ecommerce.paymentservice.repository;

import com.ecommerce.paymentservice.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    /** "At most one non-FAILED Payment per orderId" (invariant 4) — most recent one wins for PAY2. */
    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(String orderId);
}
