package com.ecommerce.orderservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * {@code OrderItem} (Entity) — composed inside {@code OrderAggregate}, not an independent
 * aggregate — specs/iter2/3-domain-models-order-service-ddd.md §2.1.1. {@code unitPrice} is a
 * snapshot of the Product Service price at placement time, unaffected by later price changes.
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_amount", nullable = false)
    private BigDecimal unitPriceAmount;

    @Column(name = "unit_price_currency", nullable = false)
    private String unitPriceCurrency;

    protected OrderItem() {
        // JPA
    }

    public OrderItem(String id, String productId, int quantity, BigDecimal unitPriceAmount, String unitPriceCurrency) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPriceAmount = unitPriceAmount;
        this.unitPriceCurrency = unitPriceCurrency;
    }

    public String getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    void setOrder(Order order) {
        this.order = order;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPriceAmount() {
        return unitPriceAmount;
    }

    public String getUnitPriceCurrency() {
        return unitPriceCurrency;
    }
}
