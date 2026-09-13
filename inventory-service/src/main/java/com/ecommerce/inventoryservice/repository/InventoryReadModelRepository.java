package com.ecommerce.inventoryservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for the I1 read model. */
public interface InventoryReadModelRepository extends JpaRepository<InventoryReadModel, String> {
}
