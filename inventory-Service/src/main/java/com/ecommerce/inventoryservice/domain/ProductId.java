package com.ecommerce.inventoryservice.domain;

import jakarta.persistence.AttributeConverter;

/**
 * Value Object — specs/iter2/3-domain-models-inventory-service-ddd.md §2.2.2.
 * A cross-service reference to a product, carried as an ID only.
 */
public record ProductId(String value) {

    public ProductId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }

    /** This product's stock stream is keyed by an equivalent {@link StockItemId} (1:1, §3.2). */
    public StockItemId asStockItemId() {
        return new StockItemId(value);
    }

    public static class JpaConverter implements AttributeConverter<ProductId, String> {

        @Override
        public String convertToDatabaseColumn(ProductId attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public ProductId convertToEntityAttribute(String dbData) {
            return dbData == null ? null : new ProductId(dbData);
        }
    }
}
