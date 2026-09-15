package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.dto.ProductCreateRequest;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.dto.ProductUpdateRequest;
import com.ecommerce.productservice.exception.ApiException;
import com.ecommerce.productservice.repository.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Command application service - 6-cqrs-command-query.md §3. Covers P4.
 *
 * <p>Orchestration only: every business rule (non-negative price, INACTIVE being terminal,
 * which events to raise) belongs to the Product aggregate. This class loads, delegates,
 * saves, and publishes whatever the aggregate raised.
 */
@Service
@Transactional
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProductCommandService(ProductRepository productRepository, ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    public ProductResponse create(ProductCreateRequest request) {
        Product product = Product.create(
                nextProductId(),
                request.name(),
                request.description(),
                ProductMapper.toMoney(request.price()),
                request.imageUrl(),
                request.categoryId());

        productRepository.save(product);
        publishEvents(product);
        return ProductMapper.toResponse(product);
    }

    public ProductResponse update(String id, ProductUpdateRequest request) {
        Product product = getOrThrow(id);
        product.update(
                request.name(),
                request.description(),
                ProductMapper.toMoney(request.price()),
                request.imageUrl(),
                request.categoryId());

        productRepository.save(product);
        publishEvents(product);
        return ProductMapper.toResponse(product);
    }

    public ProductResponse deactivate(String id) {
        Product product = getOrThrow(id);
        product.deactivate();

        productRepository.save(product);
        publishEvents(product);
        return ProductMapper.toResponse(product);
    }

    private Product getOrThrow(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private void publishEvents(Product product) {
        product.pullDomainEvents().forEach(eventPublisher::publishEvent);
    }

    /** Matches the "PRD-" shape used throughout the spec examples. */
    private static String nextProductId() {
        return "PRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
