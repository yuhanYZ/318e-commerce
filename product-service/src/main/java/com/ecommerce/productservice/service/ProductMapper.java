package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.Money;
import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.dto.MoneyDto;
import com.ecommerce.productservice.dto.ProductDetailResponse;
import com.ecommerce.productservice.dto.ProductResponse;

/** Translates between the aggregate and the wire DTOs defined in openapi-contract-v2.yaml. */
final class ProductMapper {

    private ProductMapper() {
    }

    static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                new MoneyDto(product.getPrice().amount(), product.getPrice().currency()),
                product.getImageUrl(),
                product.getCategoryId(),
                product.getStatus().name());
    }

    static ProductDetailResponse toDetailResponse(Product product, int stockAvailable, boolean inStock) {
        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                new MoneyDto(product.getPrice().amount(), product.getPrice().currency()),
                product.getImageUrl(),
                product.getCategoryId(),
                product.getStatus().name(),
                stockAvailable,
                inStock);
    }

    /** Null-safe: a partial update may omit price entirely. */
    static Money toMoney(MoneyDto dto) {
        return dto == null ? null : new Money(dto.amount(), dto.currency());
    }
}
