package com.ecommerce.orderservice.repository;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderId;
import com.ecommerce.orderservice.domain.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, OrderId> {

    Page<Order> findByUserId(UserId userId, Pageable pageable);
}
