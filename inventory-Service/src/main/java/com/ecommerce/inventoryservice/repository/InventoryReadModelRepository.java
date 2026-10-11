package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.InventoryReadModel;
import com.ecommerce.inventoryservice.domain.ProductId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReadModelRepository extends JpaRepository<InventoryReadModel, ProductId> {
}
