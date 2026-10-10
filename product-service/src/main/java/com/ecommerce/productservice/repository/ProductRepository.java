package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.domain.CategoryId;
import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.domain.ProductId;
import com.ecommerce.productservice.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, ProductId> {

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findByStatusAndCategoryId(ProductStatus status, CategoryId categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND "
            + "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Product> search(@Param("status") ProductStatus status, @Param("keyword") String keyword);
}
