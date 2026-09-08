package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.request.ProductCreateRequest;
import com.ecommerce.productservice.dto.request.ProductUpdateRequest;
import com.ecommerce.productservice.dto.response.ProductDetailResponse;
import com.ecommerce.productservice.dto.response.ProductPageResponse;
import com.ecommerce.productservice.dto.response.ProductResponse;
import com.ecommerce.productservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Presentation layer for the Product Service. Performs basic request/response
 * plumbing only - no business logic - per specs/1-technical-architecture.md.
 * Endpoint shapes follow specs/4-api-endpoints.md and specs/openapi-contract.yaml.
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** P1. */
    @GetMapping
    public ResponseEntity<ProductPageResponse> browseProducts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(productService.browseProducts(categoryId, page, size));
    }

    /** P3. Must be registered before /{id} so "search" is never matched as a product id. */
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(@RequestParam("q") String keyword) {
        return ResponseEntity.ok(productService.searchProducts(keyword));
    }

    /** P2. */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> getProductById(@PathVariable String id) {
        return ResponseEntity.ok(productService.getProductDetail(id));
    }

    /** P4 (create, Admin only - enforced in SecurityConfig). */
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    /** P4 (update, Admin only). */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    /** P4 (deactivate, Admin only). */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ProductResponse> deactivateProduct(@PathVariable String id) {
        return ResponseEntity.ok(productService.deactivateProduct(id));
    }
}
