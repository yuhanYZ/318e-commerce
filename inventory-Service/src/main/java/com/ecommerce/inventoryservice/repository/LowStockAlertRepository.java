package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.LowStockAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LowStockAlertRepository extends JpaRepository<LowStockAlert, String> {

    /** At most one unresolved alert per StockItem at a time (I4 step iii). */
    Optional<LowStockAlert> findByStockItemIdAndResolvedAtIsNull(String stockItemId);

    /** I4: list active alerts. */
    List<LowStockAlert> findAllByResolvedAtIsNull();
}
