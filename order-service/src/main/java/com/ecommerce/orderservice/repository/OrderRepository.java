package com.ecommerce.orderservice.repository;

import com.ecommerce.orderservice.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, String> {

    /** O3: current user's order history, newest first. */
    Page<Order> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
