package com.valentin.inventoryservice.api;

import com.valentin.inventoryservice.dto.AddStockRequest;
import com.valentin.inventoryservice.dto.CreateProductRequest;
import com.valentin.inventoryservice.dto.InventoryProductResponse;
import com.valentin.inventoryservice.dto.ProductResponse;
import com.valentin.inventoryservice.logic.ProductService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@AllArgsConstructor
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductResponse response = productService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/stock")
    public ResponseEntity<InventoryProductResponse> addStock(
            @PathVariable UUID id,
            @Valid @RequestBody AddStockRequest request
    ) {
        InventoryProductResponse response = productService.addStock(
                id,
                request.quantity()
        );
        return ResponseEntity.ok(response);
    }


    @GetMapping("/{id}/stock")
    public ResponseEntity<InventoryProductResponse> getProductStockById(
            @PathVariable UUID id
    ) {
        InventoryProductResponse response = productService.findProductStockById(id);

        return ResponseEntity.ok(response);
    }
}

