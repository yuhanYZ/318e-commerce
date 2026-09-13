package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.LowStockAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Persistence for the CRUD {@code LowStockAlertAggregate}. */
public interface LowStockAlertRepository extends JpaRepository<LowStockAlert, String> {

    /** The open alert for a stock item, if any. The aggregate invariant allows at most one. */
    Optional<LowStockAlert> findByStockItemIdAndResolvedAtIsNull(String stockItemId);

    /** I4: active alerts only, oldest first. */
    List<LowStockAlert> findByResolvedAtIsNullOrderByTriggeredAtAsc();
}
