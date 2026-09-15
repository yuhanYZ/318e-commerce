package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Infrastructure layer persistence port for the Product aggregate. */
public interface ProductRepository extends JpaRepository<Product, String> {

    /** P1 - browse all active products. */
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    /** P1 - browse active products within one category. */
    Page<Product> findByStatusAndCategoryId(ProductStatus status, String categoryId, Pageable pageable);

    /**
     * P3 - keyword match over name and description, ranked: name hits come before
     * description-only hits, then alphabetically. Same ACTIVE-only scope as browsing.
     */
    @Query("""
            select p from Product p
            where p.status = :status
              and (lower(p.name) like lower(concat('%', :keyword, '%'))
                or lower(p.description) like lower(concat('%', :keyword, '%')))
            order by case when lower(p.name) like lower(concat('%', :keyword, '%')) then 0 else 1 end,
                     p.name asc
            """)
    List<Product> search(@Param("status") ProductStatus status, @Param("keyword") String keyword);
}
