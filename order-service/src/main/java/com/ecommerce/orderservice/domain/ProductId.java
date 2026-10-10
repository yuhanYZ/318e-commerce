package com.ecommerce.orderservice.domain;

import jakarta.persistence.AttributeConverter;

/** Value Object — specs/iter2/3-domain-models-order-service-ddd.md §2.2.3. Cross-service ID only. */
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
