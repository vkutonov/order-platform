package com.valentin.inventoryservice.logic;

import com.valentin.inventoryservice.db.InventoryItemRepository;
import com.valentin.inventoryservice.db.ProductRepository;
import com.valentin.inventoryservice.domain.InventoryItemEntity;
import com.valentin.inventoryservice.domain.ProductEntity;
import com.valentin.inventoryservice.domain.dictionary.ProductStatus;
import com.valentin.inventoryservice.dto.CreateProductRequest;
import com.valentin.inventoryservice.dto.InventoryProductResponse;
import com.valentin.inventoryservice.dto.ProductSnapshotResponse;
import com.valentin.inventoryservice.exception.ProductNotFoundException;
import com.valentin.inventoryservice.mapper.InventoryProductMapper;
import com.valentin.inventoryservice.mapper.ProductMapper;
import com.valentin.inventoryservice.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final ProductMapper productMapper;
    private final InventoryProductMapper inventoryProductMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<InventoryProductResponse> findAllProducts() {
        List<ProductEntity> products = productRepository.findAllByStatusNot(ProductStatus.DELETED);
        if (products.isEmpty()) {
            return List.of();
        }

        List<UUID> productIds = products.stream().map(ProductEntity::getId).toList();
        Map<UUID, InventoryItemEntity> items = inventoryItemRepository.findAllByProductIdIn(productIds)
                .stream()
                .collect(Collectors.toMap(InventoryItemEntity::getProductId, Function.identity()));

        return products.stream()
                .map(product -> {
                    InventoryItemEntity item = items.get(product.getId());
                    if (item == null) {
                        throw new ProductNotFoundException(product.getId());
                    }
                    return inventoryProductMapper.toResponse(product, item);
                })
                .toList();
    }

    @Transactional
    public ProductResponse create(CreateProductRequest productRequest) {

        Instant timeNow = clock.instant();

        ProductEntity product = ProductEntity.create(
                productRequest.name(),
                productRequest.description(),
                productRequest.unitPrice(),
                productRequest.currency(),
                ProductStatus.ACTIVE,
                timeNow
        );

        ProductEntity savedProduct = productRepository.save(product);

        log.info("Product created: productId={}, name={}, price={}",
                savedProduct.getId(),
                savedProduct.getName(),
                savedProduct.getUnitPrice()
        );

        InventoryItemEntity item = InventoryItemEntity.create(
                savedProduct.getId(),
                productRequest.quantityOnHand(),
                timeNow
        );

        inventoryItemRepository.save(item);


        return productMapper.toProductResponse(savedProduct);
    }

    @Transactional
    public void deleteProduct(UUID productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (product.getStatus() == ProductStatus.DELETED) {
            return;
        }

        product.markDeleted(clock.instant());

        log.info("Product deleted: productId={}", productId);
    }


    @Transactional(readOnly = true)
    public InventoryProductResponse findProductStockById(UUID productId) {
        InventoryItemEntity item = findItemByProductId(productId);
        ProductEntity product = findProduct(productId);

        return inventoryProductMapper.toResponse(product, item);
    }


    @Transactional
    public InventoryProductResponse addStock(
            UUID productId,
            int quantity
    ) {
        ProductEntity product = findProduct(productId);
        InventoryItemEntity item = findItemByProductId(productId);
        Instant updatedAt = clock.instant();

        item.addStock(quantity, updatedAt);

        log.info("Add product stock: productId={}, quantity={}",
                productId,
                quantity
        );

        return inventoryProductMapper.toResponse(product, item);
    }


    @Transactional(readOnly = true)
    public List<ProductSnapshotResponse> findProducts(Set<UUID> productIds) {
        Map<UUID, ProductEntity> products = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(
                        ProductEntity::getId,
                        Function.identity()
                ));

        Set<UUID> missingIds = new HashSet<>(productIds);
        missingIds.removeAll(products.keySet());

        if (!missingIds.isEmpty()) {
            throw new ProductNotFoundException(missingIds);
        }

        return products.values().stream()
                .map(product -> new ProductSnapshotResponse(
                        product.getId(),
                        product.getName(),
                        product.getUnitPrice(),
                        product.getCurrency(),
                        product.getStatus()
                ))
                .toList();

    }

    private InventoryItemEntity findItemByProductId(UUID productId) {
        return inventoryItemRepository.findByProductId(productId).orElseThrow(() ->
                new ProductNotFoundException(productId)
        );
    }


    private ProductEntity findProduct(UUID id) {
        return productRepository.findById(id)
                .filter(product -> product.getStatus() != ProductStatus.DELETED)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
