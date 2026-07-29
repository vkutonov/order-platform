package com.valentin.inventoryservice.logic;

import com.valentin.inventoryservice.db.InventoryItemRepository;
import com.valentin.inventoryservice.db.ProductRepository;
import com.valentin.inventoryservice.domain.InventoryItemEntity;
import com.valentin.inventoryservice.domain.ProductEntity;
import com.valentin.inventoryservice.domain.dictionary.ProductStatus;
import com.valentin.inventoryservice.dto.CreateProductRequest;
import com.valentin.inventoryservice.dto.InventoryProductResponse;
import com.valentin.inventoryservice.dto.ProductResponse;
import com.valentin.inventoryservice.exception.ProductNotFoundException;
import com.valentin.inventoryservice.mapper.InventoryProductMapper;
import com.valentin.inventoryservice.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T10:00:00Z");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private ProductRepository productRepository;
    private InventoryItemRepository inventoryItemRepository;
    private ProductMapper productMapper;
    private InventoryProductMapper inventoryProductMapper;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        inventoryItemRepository = mock(InventoryItemRepository.class);
        productMapper = mock(ProductMapper.class);
        inventoryProductMapper = mock(InventoryProductMapper.class);

        productService = new ProductService(
                productRepository,
                inventoryItemRepository,
                productMapper,
                inventoryProductMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createSavesProductAndInitialInventoryItem() {
        CreateProductRequest request = new CreateProductRequest(
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("5000.00"),
                "RUB",
                10
        );
        ProductEntity savedProduct = product(PRODUCT_ID);
        ProductResponse expectedResponse = new ProductResponse(
                PRODUCT_ID,
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("5000.00"),
                "RUB",
                ProductStatus.ACTIVE,
                NOW,
                NOW
        );

        when(productRepository.save(any(ProductEntity.class))).thenReturn(savedProduct);
        when(productMapper.toProductResponse(savedProduct)).thenReturn(expectedResponse);

        ProductResponse result = productService.create(request);

        ArgumentCaptor<InventoryItemEntity> inventoryCaptor =
                ArgumentCaptor.forClass(InventoryItemEntity.class);

        verify(inventoryItemRepository).save(inventoryCaptor.capture());

        InventoryItemEntity savedInventory = inventoryCaptor.getValue();
        assertThat(savedInventory.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(savedInventory.getQuantityOnHand()).isEqualTo(10);
        assertThat(savedInventory.getReservedQuantity()).isZero();
        assertThat(savedInventory.getCreatedAt()).isEqualTo(NOW);
        assertThat(result).isSameAs(expectedResponse);
    }

    @Test
    void findProductStockReturnsMappedProductAndInventory() {
        ProductEntity product = product(PRODUCT_ID);
        InventoryItemEntity inventoryItem = InventoryItemEntity.create(PRODUCT_ID, 8, NOW);
        InventoryProductResponse expectedResponse = inventoryResponse(8, 0);

        when(inventoryItemRepository.findByProductId(PRODUCT_ID))
                .thenReturn(Optional.of(inventoryItem));
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(inventoryProductMapper.toResponse(product, inventoryItem))
                .thenReturn(expectedResponse);

        InventoryProductResponse result =
                productService.findProductStockById(PRODUCT_ID);

        assertThat(result).isSameAs(expectedResponse);
    }

    @Test
    void addStockUpdatesQuantityAndReturnsMappedResponse() {
        ProductEntity product = product(PRODUCT_ID);
        InventoryItemEntity inventoryItem = InventoryItemEntity.create(PRODUCT_ID, 5, NOW.minusSeconds(60));
        InventoryProductResponse expectedResponse = inventoryResponse(8, 0);

        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(inventoryItemRepository.findByProductId(PRODUCT_ID))
                .thenReturn(Optional.of(inventoryItem));
        when(inventoryProductMapper.toResponse(product, inventoryItem))
                .thenReturn(expectedResponse);

        InventoryProductResponse result =
                productService.addStock(PRODUCT_ID, 3);

        assertThat(inventoryItem.getQuantityOnHand()).isEqualTo(8);
        assertThat(inventoryItem.getUpdatedAt()).isEqualTo(NOW);
        assertThat(result).isSameAs(expectedResponse);
    }

    @Test
    void addStockForMissingProductThrowsProductNotFound() {
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.addStock(PRODUCT_ID, 3))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(PRODUCT_ID.toString());

        verifyNoInteractions(inventoryItemRepository);
    }

    private ProductEntity product(UUID productId) {
        ProductEntity product = mock(ProductEntity.class);
        when(product.getId()).thenReturn(productId);
        when(product.getName()).thenReturn("Keyboard");
        when(product.getUnitPrice()).thenReturn(new BigDecimal("5000.00"));
        when(product.getStatus()).thenReturn(ProductStatus.ACTIVE);
        return product;
    }

    private InventoryProductResponse inventoryResponse(
            int quantityOnHand,
            int reservedQuantity
    ) {
        return new InventoryProductResponse(
                PRODUCT_ID,
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("5000.00"),
                "RUB",
                ProductStatus.ACTIVE,
                quantityOnHand,
                reservedQuantity,
                quantityOnHand - reservedQuantity
        );
    }
}
