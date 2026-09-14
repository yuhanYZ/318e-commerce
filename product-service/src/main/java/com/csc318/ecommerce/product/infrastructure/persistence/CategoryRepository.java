package com.csc318.ecommerce.product.infrastructure.persistence;
import com.csc318.ecommerce.product.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CategoryRepository extends JpaRepository<Category,String>{}
