package com.csc318.ecommerce.product.service;

import com.csc318.ecommerce.product.domain.*;
import com.csc318.ecommerce.product.infrastructure.inventory.InventoryClient;
import com.csc318.ecommerce.product.infrastructure.persistence.*;
import com.csc318.ecommerce.product.presentation.ProductDtos.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional
public class ProductApplicationService {
    private final ProductRepository products;private final CategoryRepository categories;private final InventoryClient inventory;
    public ProductApplicationService(ProductRepository products,CategoryRepository categories,InventoryClient inventory){
        this.products=products;this.categories=categories;this.inventory=inventory;
    }
    @Transactional(readOnly=true)
    public ProductPageResponse browse(String categoryId,int page,int size){
        Pageable pageable=PageRequest.of(page,size);Page<Product> result=categoryId==null?
                products.findByStatus(ProductStatus.ACTIVE,pageable):products.findByStatusAndCategoryId(ProductStatus.ACTIVE,categoryId,pageable);
        return new ProductPageResponse(result.stream().map(ProductApplicationService::response).toList(),page,result.getTotalElements());
    }
    @Transactional(readOnly=true)
    public ProductDetailResponse detail(String id){Product p=requireActive(id);int stock=inventory.stock(id);
        return new ProductDetailResponse(p.getId(),p.getName(),p.getDescription(),p.getPrice(),p.getImageUrl(),p.getCategoryId(),p.getStatus(),stock,stock>0);}
    @Transactional(readOnly=true)
    public List<ProductResponse> search(String query){
        if(query==null||query.isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUERY","Search query must not be blank");
        return products.findByStatusAndNameContainingIgnoreCaseOrStatusAndDescriptionContainingIgnoreCase(
                ProductStatus.ACTIVE,query.trim(),ProductStatus.ACTIVE,query.trim()).stream().map(ProductApplicationService::response).toList();
    }
    public ProductResponse create(ProductCreateRequest req){
        if(!categories.existsById(req.categoryId()))throw new ApiException(HttpStatus.BAD_REQUEST,"CATEGORY_NOT_FOUND","Category not found");
        return response(products.save(new Product(id(),req.name(),req.description(),req.price(),req.imageUrl(),req.categoryId())));
    }
    public ProductResponse update(String id,ProductUpdateRequest req){Product p=requireAny(id);p.update(req.name(),req.description(),req.price(),req.imageUrl());return response(p);}
    public ProductResponse deactivate(String id){Product p=requireAny(id);p.deactivate();return response(p);}
    private Product requireActive(String id){return products.findById(id).filter(p->p.getStatus()==ProductStatus.ACTIVE)
            .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","Product not found"));}
    private Product requireAny(String id){return products.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","Product not found"));}
    private static ProductResponse response(Product p){return new ProductResponse(p.getId(),p.getName(),p.getDescription(),p.getPrice(),p.getImageUrl(),p.getCategoryId(),p.getStatus());}
    private static String id(){return "PRD-"+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase();}
}
