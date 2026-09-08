package com.ecommerce.paymentservice.repository;

import com.ecommerce.paymentservice.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    /** PAY2. */
    Optional<Payment> findByOrderId(String orderId);
}
