package com.ecommerce.productservice.config;

import com.ecommerce.productservice.domain.Category;
import com.ecommerce.productservice.domain.CategoryId;
import com.ecommerce.productservice.domain.Money;
import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.domain.ProductId;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Provides a small, stable catalogue for local development and demos.
 *
 * <p>The IDs and product details are deliberately fixed. The existence checks make this safe to
 * run at every application startup, including when the datasource is later changed from H2 to a
 * persistent database.
 */
@Component
public class ProductCatalogSeeder implements ApplicationRunner {

    private static final Instant SEEDED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private static final List<SeedCategory> CATEGORIES = List.of(
            new SeedCategory("CAT-HOME", "Home & Living"),
            new SeedCategory("CAT-TECH", "Technology"),
            new SeedCategory("CAT-LIFESTYLE", "Lifestyle")
    );

    private static final List<SeedProduct> PRODUCTS = List.of(
            new SeedProduct("PRD-AURORA-LAMP", "Aurora Table Lamp",
                    "Warm, dimmable LED lamp for a calm desk or bedside setup.", "49.90", "CAT-HOME"),
            new SeedProduct("PRD-OAK-DESK", "Oakline Desk Organiser",
                    "Solid oak tray set that keeps stationery and small essentials in order.", "34.50", "CAT-HOME"),
            new SeedProduct("PRD-WIRELESS-KEYBOARD", "Nimbus Wireless Keyboard",
                    "Compact wireless keyboard with quiet keys and multi-device pairing.", "89.00", "CAT-TECH"),
            new SeedProduct("PRD-ERGONOMIC-MOUSE", "Contour Ergonomic Mouse",
                    "Comfort-focused wireless mouse designed for all-day work.", "59.90", "CAT-TECH"),
            new SeedProduct("PRD-INSULATED-BOTTLE", "Summit Insulated Bottle",
                    "Double-wall stainless-steel bottle that keeps drinks cold or hot.", "29.95", "CAT-LIFESTYLE"),
            new SeedProduct("PRD-WEEKEND-TOTE", "Weekender Canvas Tote",
                    "Durable everyday tote with an inside pocket and reinforced handles.", "42.00", "CAT-LIFESTYLE")
    );

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public ProductCatalogSeeder(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        CATEGORIES.forEach(category -> {
            CategoryId id = new CategoryId(category.id());
            if (!categoryRepository.existsById(id)) {
                categoryRepository.save(new Category(id, category.name()));
            }
        });

        PRODUCTS.forEach(product -> {
            ProductId id = new ProductId(product.id());
            if (!productRepository.existsById(id)) {
                Product seededProduct = Product.create(id, product.name(), product.description(),
                        new Money(new BigDecimal(product.price()), "AUD"), null,
                        new CategoryId(product.categoryId()), SEEDED_AT);
                productRepository.save(seededProduct);
                // Seed data is infrastructure setup, not a user-initiated catalog change.
                seededProduct.pullDomainEvents();
            }
        });
    }

    private record SeedCategory(String id, String name) {
    }

    private record SeedProduct(String id, String name, String description, String price, String categoryId) {
    }
}
