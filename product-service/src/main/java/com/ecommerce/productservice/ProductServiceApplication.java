package com.ecommerce.productservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Product Service (P1-P4) - specs/iter2/2-user-stories-product-service-v2.md.
 *
 * <p>Catalog browsing, search, and admin product management. Runs on port 8082.
 * Stock is not owned here; P2 fetches it from Inventory Service over REST.
 */
@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
