package com.csc318.ecommerce.product.presentation;

import com.csc318.ecommerce.product.presentation.ProductDtos.*;
import com.csc318.ecommerce.product.service.ProductApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductApplicationService service;
    public ProductController(ProductApplicationService service){this.service=service;}
    @GetMapping public ProductPageResponse browse(@RequestParam(required=false)String categoryId,
            @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.browse(categoryId,page,size);}
    @GetMapping("/{id}") public ProductDetailResponse detail(@PathVariable String id){return service.detail(id);}
    @GetMapping("/search") public List<ProductResponse> search(@RequestParam String q){return service.search(q);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ProductResponse create(@Valid @RequestBody ProductCreateRequest req){return service.create(req);}
    @PutMapping("/{id}") public ProductResponse update(@PathVariable String id,@Valid @RequestBody ProductUpdateRequest req){return service.update(id,req);}
    @PatchMapping("/{id}/deactivate") public ProductResponse deactivate(@PathVariable String id){return service.deactivate(id);}
}
