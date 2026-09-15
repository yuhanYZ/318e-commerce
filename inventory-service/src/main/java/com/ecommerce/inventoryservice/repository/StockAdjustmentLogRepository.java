package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.StockAdjustmentLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for the append-only adjustment audit log. */
public interface StockAdjustmentLogRepository extends JpaRepository<StockAdjustmentLog, String> {
}
