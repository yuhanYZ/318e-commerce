package com.csc318.ecommerce.product.infrastructure.persistence;

import com.csc318.ecommerce.product.domain.Category;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {
    private final CategoryRepository categories;
    public DataSeeder(CategoryRepository categories){this.categories=categories;}
    @Override public void run(String...args){if(!categories.existsById("CAT-DEFAULT"))categories.save(new Category("CAT-DEFAULT","General",null));}
}
