package com.valentin.orderservice.client;

import com.valentin.orderservice.dto.ProductSnapshot;
import com.valentin.orderservice.dto.ProductsBatchRequest;

import java.util.List;

public interface InventoryClient {

    List<ProductSnapshot> getProductsSnapshot(
            ProductsBatchRequest request
    );
}
