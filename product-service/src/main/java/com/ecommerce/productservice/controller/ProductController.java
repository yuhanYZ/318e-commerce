package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.ProductCreateRequest;
import com.ecommerce.productservice.dto.ProductDetailResponse;
import com.ecommerce.productservice.dto.ProductPageResponse;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.dto.ProductUpdateRequest;
import com.ecommerce.productservice.service.ProductCommandService;
import com.ecommerce.productservice.service.ProductQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Presentation layer for P1-P4 - 4-api-endpoints-v2.md §2.
 *
 * <p>HTTP in, HTTP out. No business logic: requests are shaped and handed to the command or
 * query application service, matching the CQRS split in 6-cqrs-command-query.md §3.
 * Authorization for the admin-only operations is enforced by SecurityConfig, not here.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductCommandService commandService;
    private final ProductQueryService queryService;

    public ProductController(ProductCommandService commandService, ProductQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /** P1 - browse the catalog, optionally filtered by category. */
    @GetMapping
    public ProductPageResponse browseProducts(@RequestParam(required = false) String categoryId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return queryService.browseProducts(categoryId, page, size);
    }

    /**
     * P3 - keyword search. Kept next to the {id} mapping deliberately: Spring matches the
     * literal /search path ahead of the template, and seeing them together makes that obvious.
     */
    @GetMapping("/search")
    public List<ProductResponse> searchProducts(@RequestParam String q) {
        return queryService.searchProducts(q);
    }

    /** P2 - product details including live stock from Inventory Service. */
    @GetMapping("/{id}")
    public ProductDetailResponse getProductById(@PathVariable String id) {
        return queryService.getProductById(id);
    }

    /** P4 - create. Admin only. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return commandService.create(request);
    }

    /** P4 - partial update. Admin only. */
    @PutMapping("/{id}")
    public ProductResponse updateProduct(@PathVariable String id,
                                         @Valid @RequestBody ProductUpdateRequest request) {
        return commandService.update(id, request);
    }

    /** P4 - deactivate. Admin only. INACTIVE is terminal. */
    @PatchMapping("/{id}/deactivate")
    public ProductResponse deactivateProduct(@PathVariable String id) {
        return commandService.deactivate(id);
    }
}
