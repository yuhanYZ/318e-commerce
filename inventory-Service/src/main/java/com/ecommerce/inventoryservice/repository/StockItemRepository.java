package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockItemRepository extends JpaRepository<StockItem, String> {
    Optional<StockItem> findByProductId(String productId);
}
