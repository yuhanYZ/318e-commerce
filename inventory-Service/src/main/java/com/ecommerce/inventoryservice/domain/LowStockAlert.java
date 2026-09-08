package com.ecommerce.inventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents one triggered low-stock alert (I4), per specs/3-domain-models.md
 * section 5.3. {@code resolvedAt} stays null while the alert is active;
 * InventoryService only opens a new alert for a StockItem once any existing
 * unresolved alert for it has been resolved, per User Story I4 step iii
 * ("no duplicate trigger before resolution").
 */
@Entity
@Table(name = "low_stock_alerts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockAlert {

    @Id
    private String id;

    @Column(nullable = false)
    private String stockItemId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime triggeredAt;

    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (triggeredAt == null) {
            triggeredAt = LocalDateTime.now();
        }
    }
}
