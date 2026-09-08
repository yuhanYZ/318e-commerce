package com.ecommerce.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A refund request against a {@link Payment} (PAY3, PAY4). Maps to the
 * {@code Refund} domain class in specs/3-domain-models.md section 6.2.
 * Every refund request is persisted regardless of outcome - including
 * ineligible ones, saved as REJECTED - rather than only ever recording
 * successes, so PAY4's "financial reconciliation" use case (per User Story
 * PAY4) has a complete audit trail. specs/3-domain-models.md flags "does this
 * support partial refunds?" as an open question; resolved here as: yes, as
 * long as {@code amount <= Payment.amount} (see PaymentService.requestRefund).
 */
@Entity
@Table(name = "refunds")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund {

    @Id
    private String id;

    @Column(nullable = false)
    private String paymentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RefundStatus status = RefundStatus.REQUESTED;

    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
    }
}
