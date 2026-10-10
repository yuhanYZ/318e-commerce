package com.ecommerce.inventoryservice.domain;

import jakarta.persistence.AttributeConverter;

/**
 * Value Object — specs/iter2/3-domain-models-inventory-service-ddd.md §2.2.1.
 *
 * <p>Takes the same value as the {@code ProductId} of the product it tracks: one product maps to
 * exactly one {@code StockItem} stream (see §3.2 of {@code 5-event-sourcing.md}), which is why the
 * two are interchangeable for stream lookup.
 */
public record StockItemId(String value) {

    public StockItemId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("stockItemId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }

    public static class JpaConverter implements AttributeConverter<StockItemId, String> {

        @Override
        public String convertToDatabaseColumn(StockItemId attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public StockItemId convertToEntityAttribute(String dbData) {
            return dbData == null ? null : new StockItemId(dbData);
        }
    }
}
