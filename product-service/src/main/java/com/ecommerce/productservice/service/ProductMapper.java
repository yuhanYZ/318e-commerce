package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.dto.MoneyDto;
import com.ecommerce.productservice.dto.ProductDetailResponse;
import com.ecommerce.productservice.dto.ProductResponse;

final class ProductMapper {

    private ProductMapper() {
    }

    static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                new MoneyDto(product.getPriceAmount(), product.getPriceCurrency()), product.getImageUrl(),
                product.getCategoryId(), product.getStatus().name());
    }

    static ProductDetailResponse toDetailResponse(Product product, int stockAvailable, boolean isInStock) {
        return new ProductDetailResponse(product.getId(), product.getName(), product.getDescription(),
                new MoneyDto(product.getPriceAmount(), product.getPriceCurrency()), product.getImageUrl(),
                product.getCategoryId(), product.getStatus().name(), stockAvailable, isInStock);
    }
}
