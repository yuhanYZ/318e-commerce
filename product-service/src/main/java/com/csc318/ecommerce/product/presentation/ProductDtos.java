package com.csc318.ecommerce.product.presentation;

import com.csc318.ecommerce.product.domain.ProductStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class ProductDtos {
    private ProductDtos() {}
    public record ProductCreateRequest(@NotBlank String name, String description,
            @NotNull @DecimalMin("0.0") BigDecimal price, String imageUrl, @NotBlank String categoryId) {}
    public record ProductUpdateRequest(String name, String description, @DecimalMin("0.0") BigDecimal price, String imageUrl) {}
    public record ProductResponse(String id,String name,String description,BigDecimal price,String imageUrl,String categoryId,ProductStatus status) {}
    public record ProductDetailResponse(String id,String name,String description,BigDecimal price,String imageUrl,String categoryId,
                                        ProductStatus status,int stockAvailable,boolean isInStock) {}
    public record ProductPageResponse(List<ProductResponse> content,int page,long totalElements) {}
}
