package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.domain.ProductStatus;
import com.ecommerce.productservice.dto.external.StockResponse;
import com.ecommerce.productservice.dto.request.ProductCreateRequest;
import com.ecommerce.productservice.dto.request.ProductUpdateRequest;
import com.ecommerce.productservice.dto.response.ProductDetailResponse;
import com.ecommerce.productservice.dto.response.ProductPageResponse;
import com.ecommerce.productservice.dto.response.ProductResponse;
import com.ecommerce.productservice.exception.ProductNotFoundException;
import com.ecommerce.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for the Product Service. Implements P1-P4 from
 * specs/2-user-stories.md, per the Service Layer responsibilities in
 * specs/1-technical-architecture.md.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryClient inventoryClient;

    /** P1: Browse Product Catalog - active products only, optionally filtered by category, paginated. */
    @Transactional(readOnly = true)
    public ProductPageResponse browseProducts(String categoryId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : size);
        Page<Product> result = (categoryId != null && !categoryId.isBlank())
                ? productRepository.findByStatusAndCategoryId(ProductStatus.ACTIVE, categoryId, pageable)
                : productRepository.findByStatus(ProductStatus.ACTIVE, pageable);

        List<ProductResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return new ProductPageResponse(content, result.getNumber(), result.getTotalElements());
    }

    /**
     * P3: Search Products - case-insensitive match against name/description, active
     * products only. Simplification: results are returned in repository/DB order,
     * not scored by relevance (the contract only requires "ranked", not a specific
     * algorithm, and an empty match list is a normal 200 response, not an error).
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(String keyword) {
        return productRepository.searchActiveProducts(ProductStatus.ACTIVE, keyword)
                .stream().map(this::toResponse).toList();
    }

    /** P2: View Product Details - includes a live stock lookup from Inventory Service. */
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetail(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));

        StockResponse stock = inventoryClient.getStock(product.getId());

        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getImageUrl(),
                product.getCategoryId(),
                product.getStatus().name(),
                stock.getQuantityAvailable(),
                stock.isAvailable());
    }

    /** P4: Create a new product (Admin). */
    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .imageUrl(request.getImageUrl())
                .categoryId(request.getCategoryId())
                .status(ProductStatus.ACTIVE)
                .build();
        product = productRepository.save(product);
        return toResponse(product);
    }

    /** P4: Update a product (Admin) - only non-null fields are applied. */
    @Transactional
    public ProductResponse updateProduct(String id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl());
        }
        product = productRepository.save(product);
        return toResponse(product);
    }

    /** P4: Deactivate a product (Admin) - customers stop seeing it via P1/P3 immediately. */
    @Transactional
    public ProductResponse deactivateProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));
        product.setStatus(ProductStatus.INACTIVE);
        product = productRepository.save(product);
        return toResponse(product);
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getImageUrl(),
                product.getCategoryId(),
                product.getStatus().name());
    }
}
