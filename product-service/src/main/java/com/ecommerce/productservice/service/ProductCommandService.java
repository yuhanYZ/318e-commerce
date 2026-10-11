package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.CategoryId;
import com.ecommerce.productservice.domain.Money;
import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.domain.ProductId;
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
 * Command Application Service for Product Service — specs/iter2/6-cqrs-command-query.md §3.1.
 * Covers P4 (create/update/deactivate). Every state change runs through a {@link Product}
 * behavior; this class only orchestrates load → behave → persist → dispatch.
 */
@Service
@Transactional
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final DomainEventPublisher domainEventPublisher;

    public ProductCommandService(ProductRepository productRepository,
                                 DomainEventPublisher domainEventPublisher) {
        this.productRepository = productRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    public ProductResponse create(ProductCreateRequest request) {
        Instant now = Instant.now();
        Product product = Product.create(
                new ProductId("PRD-" + shortId()),
                request.name(),
                request.description(),
                new Money(request.price().amount(), request.price().currency()),
                request.imageUrl(),
                new CategoryId(request.categoryId()),
                now);
        productRepository.save(product);
        domainEventPublisher.publish(product.pullDomainEvents());
        return ProductMapper.toResponse(product);
    }

    /** Partial update; INACTIVE is a terminal state (409 ProductStateViolation on any update attempt). */
    public ProductResponse update(String id, ProductUpdateRequest request) {
        Product product = getOrThrow(id);
        Money price = request.price() == null
                ? null
                : new Money(request.price().amount(), request.price().currency());
        CategoryId categoryId = request.categoryId() == null ? null : new CategoryId(request.categoryId());

        product.update(request.name(), request.description(), price, request.imageUrl(), categoryId, Instant.now());
        productRepository.save(product);
        domainEventPublisher.publish(product.pullDomainEvents());
        return ProductMapper.toResponse(product);
    }

    public ProductResponse deactivate(String id) {
        Product product = getOrThrow(id);
        product.deactivate(Instant.now());
        productRepository.save(product);
        domainEventPublisher.publish(product.pullDomainEvents());
        return ProductMapper.toResponse(product);
    }

    private Product getOrThrow(String id) {
        try {
            return productRepository.findById(new ProductId(id))
                    .orElseThrow(ProductCommandService::notFound);
        } catch (IllegalArgumentException ex) {
            // A blank/absent id cannot identify a product, so it is a 404 rather than a 500.
            throw notFound();
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "Product not found");
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
