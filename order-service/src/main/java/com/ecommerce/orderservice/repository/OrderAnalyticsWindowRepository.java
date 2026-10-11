package com.ecommerce.orderservice.repository;

import com.ecommerce.orderservice.domain.OrderAnalyticsWindow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OrderAnalyticsWindowRepository extends JpaRepository<OrderAnalyticsWindow, Instant> {

    List<OrderAnalyticsWindow> findByWindowStartGreaterThanEqualAndWindowStartLessThanEqual(
            Instant windowStartInclusive, Instant windowEndInclusive);
}
