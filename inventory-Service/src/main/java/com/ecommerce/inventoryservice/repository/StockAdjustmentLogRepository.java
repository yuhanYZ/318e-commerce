package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.StockAdjustmentLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Write-only audit trail for I3; nothing in I1-I5 reads it back yet. */
public interface StockAdjustmentLogRepository extends JpaRepository<StockAdjustmentLog, String> {
}
