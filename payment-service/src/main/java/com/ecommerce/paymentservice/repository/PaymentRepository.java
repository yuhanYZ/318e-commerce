package com.ecommerce.paymentservice.repository;

import com.ecommerce.paymentservice.domain.OrderId;
import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.PaymentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, PaymentId> {

    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(OrderId orderId);
}
