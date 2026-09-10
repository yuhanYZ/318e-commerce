package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.StockAdjustmentLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAdjustmentLogRepository extends JpaRepository<StockAdjustmentLog, String> {
}
