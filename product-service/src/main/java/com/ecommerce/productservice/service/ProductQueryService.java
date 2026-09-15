package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.domain.ProductStatus;
import com.ecommerce.productservice.dto.InventoryStockView;
import com.ecommerce.productservice.dto.ProductDetailResponse;
import com.ecommerce.productservice.dto.ProductPageResponse;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.exception.ApiException;
import com.ecommerce.productservice.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Query application service - 6-cqrs-command-query.md §3. Covers P1, P2, P3.
 *
 * <p>Read-only and side-effect free, per the endpoint rules in 4-api-endpoints-v2.md §7.
 */
@Service
@Transactional(readOnly = true)
public class ProductQueryService {

    private static final Logger log = LoggerFactory.getLogger(ProductQueryService.class);

    /** §7 rule 6: page defaults to 0, size to 20, capped at 100. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final RestTemplate inventoryRestTemplate;
    private final String inventoryBaseUrl;

    public ProductQueryService(ProductRepository productRepository,
                               RestTemplate inventoryRestTemplate,
                               @Value("${app.inventory-service.base-url}") String inventoryBaseUrl) {
        this.productRepository = productRepository;
        this.inventoryRestTemplate = inventoryRestTemplate;
        this.inventoryBaseUrl = inventoryBaseUrl;
    }

    /** P1 - only ACTIVE products are browsable; a deactivated product leaves the catalog. */
    public ProductPageResponse browseProducts(String categoryId, int page, int size) {
        if (page < 0) {
            throw ApiException.validationFailed("page must not be negative");
        }
        if (size < 1) {
            throw ApiException.validationFailed("size must be at least 1");
        }
        PageRequest pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));

        Page<Product> result = (categoryId == null || categoryId.isBlank())
                ? productRepository.findByStatus(ProductStatus.ACTIVE, pageable)
                : productRepository.findByStatusAndCategoryId(ProductStatus.ACTIVE, categoryId, pageable);

        List<ProductResponse> content = result.getContent().stream().map(ProductMapper::toResponse).toList();
        return new ProductPageResponse(content, result.getNumber(), result.getTotalElements());
    }

    /** P3 - same ACTIVE-only scope as browsing; no match is an empty array, not an error. */
    public List<ProductResponse> searchProducts(String keyword) {
        return productRepository.search(ProductStatus.ACTIVE, keyword).stream()
                .map(ProductMapper::toResponse)
                .toList();
    }

    /**
     * P2 - product details enriched with live stock from Inventory Service.
     *
     * <p>An unknown product is a 404. A failed stock lookup is not: per the Inter-Service Note
     * in domain-models §2.3.1, no stock record means {@code stockAvailable = 0} and
     * {@code isInStock = false}. The same fallback covers Inventory being unreachable, so the
     * catalog keeps serving while Inventory is down.
     */
    public ProductDetailResponse getProductById(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));

        int stockAvailable = 0;
        boolean inStock = false;
        try {
            InventoryStockView stock = inventoryRestTemplate.getForObject(
                    inventoryBaseUrl + "/inventory/" + productId, InventoryStockView.class);
            if (stock != null) {
                stockAvailable = stock.quantityAvailable() == null ? 0 : stock.quantityAvailable();
                inStock = stock.isAvailable() == null ? stockAvailable > 0 : stock.isAvailable();
            }
        } catch (RestClientException ex) {
            log.debug("Stock lookup failed for {}, reporting out of stock: {}", productId, ex.getMessage());
        }

        return ProductMapper.toDetailResponse(product, stockAvailable, inStock);
    }
}
