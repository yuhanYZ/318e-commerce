package com.ecommerce.paymentservice.repository;

import com.ecommerce.paymentservice.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

/** Write-mostly audit trail for PAY3 - see Refund's javadoc for why REJECTED attempts are persisted too. */
public interface RefundRepository extends JpaRepository<Refund, String> {
}
