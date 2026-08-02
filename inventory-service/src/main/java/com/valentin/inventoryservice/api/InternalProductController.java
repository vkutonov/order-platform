package com.valentin.inventoryservice.api;


import com.valentin.inventoryservice.dto.ProductSnapshotResponse;
import com.valentin.inventoryservice.dto.ProductsBatchRequest;
import com.valentin.inventoryservice.logic.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/products")
public class InternalProductController {

    private final ProductService productService;

    @PostMapping("/batch")
    public ResponseEntity<List<ProductSnapshotResponse>> getProductsBatch(
            @Valid @RequestBody ProductsBatchRequest request
    ) {
        List<ProductSnapshotResponse> products =
                productService.findProducts(request.productIds());

        return ResponseEntity.ok(products);
    }

}
