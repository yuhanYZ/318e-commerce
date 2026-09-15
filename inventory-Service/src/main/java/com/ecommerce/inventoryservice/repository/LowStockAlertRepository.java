package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.LowStockAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LowStockAlertRepository extends JpaRepository<LowStockAlert, String> {

    List<LowStockAlert> findByResolvedAtIsNull();

    Optional<LowStockAlert> findByStockItemIdAndResolvedAtIsNull(String stockItemId);
}
