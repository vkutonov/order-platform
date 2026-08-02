package com.valentin.orderservice.client;

import com.valentin.orderservice.dto.ProductSnapshot;
import com.valentin.orderservice.dto.ProductsBatchRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;


@HttpExchange(
        url = "/internal/products",
        accept = MediaType.APPLICATION_JSON_VALUE
)
public interface InventoryClient {

    @PostExchange(
            url = "/batch",
            contentType = MediaType.APPLICATION_JSON_VALUE
    )
    List<ProductSnapshot> getProductsSnapshot(
            @RequestBody ProductsBatchRequest request
    );
}
