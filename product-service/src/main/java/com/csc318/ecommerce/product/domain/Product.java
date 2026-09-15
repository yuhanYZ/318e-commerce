package com.csc318.ecommerce.product.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {
    @Id private String id;
    @Column(nullable = false) private String name;
    @Column(length = 4000) private String description;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal price;
    private String imageUrl;
    @Column(nullable = false) private String categoryId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ProductStatus status;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected Product() {}
    public Product(String id, String name, String description, BigDecimal price, String imageUrl, String categoryId) {
        this.id=id; this.name=name; this.description=description; this.price=price; this.imageUrl=imageUrl;
        this.categoryId=categoryId; this.status=ProductStatus.ACTIVE; this.createdAt=LocalDateTime.now(); this.updatedAt=createdAt;
    }
    public String getId(){return id;} public String getName(){return name;} public String getDescription(){return description;}
    public BigDecimal getPrice(){return price;} public String getImageUrl(){return imageUrl;} public String getCategoryId(){return categoryId;}
    public ProductStatus getStatus(){return status;}
    public void update(String name,String description,BigDecimal price,String imageUrl){
        if(name!=null)this.name=name; if(description!=null)this.description=description; if(price!=null)this.price=price;
        if(imageUrl!=null)this.imageUrl=imageUrl; this.updatedAt=LocalDateTime.now();
    }
    public void deactivate(){this.status=ProductStatus.INACTIVE;this.updatedAt=LocalDateTime.now();}
}
