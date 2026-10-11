package com.ecommerce.productservice.domain;

import jakarta.persistence.AttributeConverter;

/** Value Object — specs/iter2/3-domain-models-product-service-ddd.md §2.2.2. */
public record CategoryId(String value) {

    public CategoryId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("categoryId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }

    public static class JpaConverter implements AttributeConverter<CategoryId, String> {

        @Override
        public String convertToDatabaseColumn(CategoryId attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public CategoryId convertToEntityAttribute(String dbData) {
            return dbData == null ? null : new CategoryId(dbData);
        }
    }
}
