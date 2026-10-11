package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.dto.MoneyDto;
import com.ecommerce.productservice.dto.ProductDetailResponse;
import com.ecommerce.productservice.dto.ProductResponse;

/** Maps the aggregate onto the API contract's DTOs — specs/iter2/openapi-contract-v2.yaml. */
final class ProductMapper {

    private ProductMapper() {
    }

    static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId().value(), product.getName(), product.getDescription(),
                new MoneyDto(product.getPrice().amount(), product.getPrice().currency()), product.getImageUrl(),
                product.getCategoryId().value(), product.getStatus().name());
    }

    static ProductDetailResponse toDetailResponse(Product product, int stockAvailable, boolean isInStock) {
        return new ProductDetailResponse(product.getId().value(), product.getName(), product.getDescription(),
                new MoneyDto(product.getPrice().amount(), product.getPrice().currency()), product.getImageUrl(),
                product.getCategoryId().value(), product.getStatus().name(), stockAvailable, isInStock);
    }
}
