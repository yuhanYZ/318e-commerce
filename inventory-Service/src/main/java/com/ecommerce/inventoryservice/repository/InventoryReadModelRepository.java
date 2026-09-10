package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.InventoryReadModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReadModelRepository extends JpaRepository<InventoryReadModel, String> {
}
