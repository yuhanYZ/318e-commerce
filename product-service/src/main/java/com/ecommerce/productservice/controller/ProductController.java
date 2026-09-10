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

/** specs/iter2/openapi-contract-v2.yaml paths /products/*. */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductCommandService commandService;
    private final ProductQueryService queryService;

    public ProductController(ProductCommandService commandService, ProductQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    public ProductPageResponse browseProducts(@RequestParam(required = false) String categoryId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return queryService.browseProducts(categoryId, page, size);
    }

    @GetMapping("/search")
    public List<ProductResponse> searchProducts(@RequestParam String q) {
        return queryService.searchProducts(q);
    }

    @GetMapping("/{id}")
    public ProductDetailResponse getProductById(@PathVariable String id) {
        return queryService.getProductById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return commandService.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse updateProduct(@PathVariable String id, @Valid @RequestBody ProductUpdateRequest request) {
        return commandService.update(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    public ProductResponse deactivateProduct(@PathVariable String id) {
        return commandService.deactivate(id);
    }
}
