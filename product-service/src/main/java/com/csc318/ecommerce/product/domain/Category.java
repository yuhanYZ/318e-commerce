package com.csc318.ecommerce.product.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {
    @Id private String id;
    @Column(nullable = false) private String name;
    private String parentCategoryId;
    protected Category() {}
    public Category(String id, String name, String parentCategoryId) {
        this.id = id; this.name = name; this.parentCategoryId = parentCategoryId;
    }
    public String getId() { return id; }
}
