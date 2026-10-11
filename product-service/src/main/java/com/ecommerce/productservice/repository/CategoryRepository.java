package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.domain.Category;
import com.ecommerce.productservice.domain.CategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for the {@code Category} entity (specs/iter2/3-domain-models-product-service-ddd.md
 * §2.1.1). No endpoint in this iteration reads or writes categories — see {@link Category}.
 */
public interface CategoryRepository extends JpaRepository<Category, CategoryId> {
}
