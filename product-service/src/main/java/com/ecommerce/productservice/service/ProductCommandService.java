package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.domain.ProductStatus;
import com.ecommerce.productservice.dto.ProductCreateRequest;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.dto.ProductUpdateRequest;
import com.ecommerce.productservice.exception.ApiException;
import com.ecommerce.productservice.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Command Application Service for Product Service — specs/iter2/6-cqrs-command-query.md §3.
 * Covers P4 (create/update/deactivate). Every rejection path (404 / 409) is checked BEFORE any
 * repository write, so — per the lesson from user-service's Bug #3 — there is no save-then-throw
 * sequence here and no need for a noRollbackFor override.
 */
@Service
@Transactional
public class ProductCommandService {

    private final ProductRepository productRepository;

    public ProductCommandService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductResponse create(ProductCreateRequest request) {
        Product product = new Product("PRD-" + shortId());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPriceAmount(request.price().amount());
        product.setPriceCurrency(request.price().currency());
        product.setImageUrl(request.imageUrl());
        product.setCategoryId(request.categoryId());
        product.setStatus(ProductStatus.ACTIVE);
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
        return ProductMapper.toResponse(product);
    }

    /** Partial update; INACTIVE is a terminal state (409 ProductStateViolation on any update attempt). */
    public ProductResponse update(String id, ProductUpdateRequest request) {
        Product product = getOrThrow(id);
        if (product.getStatus() == ProductStatus.INACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "ProductStateViolation",
                    "An INACTIVE product cannot be updated");
        }
        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.price() != null) {
            product.setPriceAmount(request.price().amount());
            product.setPriceCurrency(request.price().currency());
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(request.imageUrl());
        }
        if (request.categoryId() != null) {
            product.setCategoryId(request.categoryId());
        }
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
        return ProductMapper.toResponse(product);
    }

    public ProductResponse deactivate(String id) {
        Product product = getOrThrow(id);
        product.setStatus(ProductStatus.INACTIVE);
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
        return ProductMapper.toResponse(product);
    }

    private Product getOrThrow(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "Product not found"));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
