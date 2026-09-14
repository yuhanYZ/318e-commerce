package com.csc318.ecommerce.product.infrastructure.inventory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryClient {
    private final RestClient client;
    public InventoryClient(RestClient.Builder builder,@Value("${app.inventory.base-url}")String baseUrl){this.client=builder.baseUrl(baseUrl).build();}
    public int stock(String productId){
        try{
            StockResponse response=client.get().uri("/inventory/{id}",productId).retrieve().body(StockResponse.class);
            return response==null?0:response.quantityAvailable();
        }catch(RuntimeException ignored){return 0;}
    }
    record StockResponse(String productId,int quantityAvailable,boolean isAvailable){}
}
