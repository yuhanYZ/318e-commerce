package com.csc318.ecommerce.product.infrastructure.persistence;

import com.csc318.ecommerce.product.domain.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProductRepository extends JpaRepository<Product,String> {
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);
    Page<Product> findByStatusAndCategoryId(ProductStatus status, String categoryId, Pageable pageable);
    List<Product> findByStatusAndNameContainingIgnoreCaseOrStatusAndDescriptionContainingIgnoreCase(
            ProductStatus nameStatus, String name, ProductStatus descriptionStatus, String description);
}
