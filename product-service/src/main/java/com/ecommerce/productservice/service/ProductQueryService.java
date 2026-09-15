package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.domain.ProductStatus;
import com.ecommerce.productservice.dto.InventoryStockView;
import com.ecommerce.productservice.dto.ProductDetailResponse;
import com.ecommerce.productservice.dto.ProductPageResponse;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.exception.ApiException;
import com.ecommerce.productservice.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Query Application Service for Product Service — specs/iter2/6-cqrs-command-query.md §3.
 * Covers P1 (browse), P2 (get by id incl. live stock), P3 (search).
 */
@Service
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final RestTemplate inventoryRestTemplate;
    private final String inventoryBaseUrl;

    public ProductQueryService(ProductRepository productRepository, RestTemplate inventoryRestTemplate,
                                @Value("${app.inventory-service.base-url}") String inventoryBaseUrl) {
        this.productRepository = productRepository;
        this.inventoryRestTemplate = inventoryRestTemplate;
        this.inventoryBaseUrl = inventoryBaseUrl;
    }

    /** P1 — only ACTIVE products are browsable. */
    public ProductPageResponse browseProducts(String categoryId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Product> result = (categoryId != null)
                ? productRepository.findByStatusAndCategoryId(ProductStatus.ACTIVE, categoryId, pageable)
                : productRepository.findByStatus(ProductStatus.ACTIVE, pageable);
        List<ProductResponse> content = result.getContent().stream().map(ProductMapper::toResponse).toList();
        return new ProductPageResponse(content, result.getNumber(), result.getTotalElements());
    }

    /** P3 — same ACTIVE-only scope as browsing; empty list (not an error) when nothing matches. */
    public List<ProductResponse> searchProducts(String keyword) {
        return productRepository.search(ProductStatus.ACTIVE, keyword).stream()
                .map(ProductMapper::toResponse)
                .toList();
    }

    /**
     * P2 — an inter-service call to Inventory Service. Any failure of that call (404 = untracked
     * product, or the service being unreachable) is treated as "stock unknown" rather than
     * failing the whole product-details request: stockAvailable=0, isInStock=false.
     */
    public ProductDetailResponse getProductById(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "Product not found"));

        int stockAvailable = 0;
        boolean isInStock = false;
        try {
            String url = inventoryBaseUrl + "/inventory/" + productId;
            InventoryStockView stock = inventoryRestTemplate.getForObject(url, InventoryStockView.class);
            if (stock != null) {
                stockAvailable = stock.quantityAvailable();
                isInStock = stock.isAvailable();
            }
        } catch (RestClientException ex) {
            // Inventory has no record for this product, or is unreachable — report as out of
            // stock rather than failing the product-details request (spec: 404 from Inventory
            // means stockAvailable=0, isInStock=false; treated the same for any other REST error).
        }

        return ProductMapper.toDetailResponse(product, stockAvailable, isInStock);
    }
}
