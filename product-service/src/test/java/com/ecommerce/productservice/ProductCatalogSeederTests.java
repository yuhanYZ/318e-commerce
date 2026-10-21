package com.ecommerce.productservice;

import com.ecommerce.productservice.domain.ProductId;
import com.ecommerce.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductCatalogSeederTests {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void seedsStableActiveProductsAtStartup() {
        assertThat(productRepository.count()).isGreaterThanOrEqualTo(6);
        assertThat(productRepository.findById(new ProductId("PRD-AURORA-LAMP")))
                .isPresent()
                .get()
                .satisfies(product -> {
                    assertThat(product.getName()).isEqualTo("Aurora Table Lamp");
                    assertThat(product.getPrice().amount()).isEqualByComparingTo("49.90");
                    assertThat(product.getPrice().currency()).isEqualTo("AUD");
                    assertThat(product.getStatus().name()).isEqualTo("ACTIVE");
                });
    }
}
