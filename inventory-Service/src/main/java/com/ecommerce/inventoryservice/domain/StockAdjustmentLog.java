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
 * Records a single Admin stock adjustment (I3), per specs/3-domain-models.md
 * section 5.2. Write-only audit trail - nothing in I1-I5 reads it back yet.
 */
@Entity
@Table(name = "stock_adjustment_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentLog {

    @Id
    private String id;

    @Column(nullable = false)
    private String stockItemId;

    @Column(nullable = false)
    private String adjustedBy;

    @Column(nullable = false)
    private int delta;

    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
