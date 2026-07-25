package com.valentin.inventoryservice.logic;

import com.valentin.inventoryservice.db.InventoryItemRepository;
import com.valentin.inventoryservice.db.ProductRepository;
import com.valentin.inventoryservice.db.ReservationRepository;
import com.valentin.inventoryservice.domain.InventoryItemEntity;
import com.valentin.inventoryservice.domain.ProductEntity;
import com.valentin.inventoryservice.domain.ReservationEntity;
import com.valentin.inventoryservice.domain.dictionary.ProductStatus;
import com.valentin.inventoryservice.domain.dictionary.ReservationFailureCode;
import com.valentin.inventoryservice.domain.dictionary.ReservationStatus;
import com.valentin.inventoryservice.dto.CreateReservationCommand;
import com.valentin.inventoryservice.dto.ReservationItemCommand;
import com.valentin.inventoryservice.dto.ReservationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T10:00:00Z");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private ReservationRepository reservationRepository;
    private ProductRepository productRepository;
    private InventoryItemRepository inventoryItemRepository;
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        productRepository = mock(ProductRepository.class);
        inventoryItemRepository = mock(InventoryItemRepository.class);

        reservationService = new ReservationService(
                reservationRepository,
                productRepository,
                inventoryItemRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.empty());
        when(reservationRepository.save(any(ReservationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void inactiveProductFailsWithoutLoadingOrChangingStock() {
        ProductEntity product = productWithStatus(ProductStatus.INACTIVE);
        when(productRepository.findAllById(any())).thenReturn(List.of(product));

        ReservationResult result = reservationService.reserve(command(1));

        assertThat(result.status()).isEqualTo(ReservationStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo(ReservationFailureCode.PRODUCT_INACTIVE);
        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void insufficientStockFailsWithoutReservingAvailableItems() {
        ProductEntity product = productWithStatus(ProductStatus.ACTIVE);
        InventoryItemEntity inventoryItem = InventoryItemEntity.create(PRODUCT_ID, 2, NOW);

        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(inventoryItemRepository.findAllByProductIdIn(any()))
                .thenReturn(List.of(inventoryItem));

        ReservationResult result = reservationService.reserve(command(3));

        assertThat(result.status()).isEqualTo(ReservationStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo(ReservationFailureCode.INSUFFICIENT_STOCK);
        assertThat(inventoryItem.getReservedQuantity()).isZero();
    }

    @Test
    void activeProductWithEnoughStockIsReserved() {
        ProductEntity product = productWithStatus(ProductStatus.ACTIVE);
        InventoryItemEntity inventoryItem = InventoryItemEntity.create(PRODUCT_ID, 5, NOW);

        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(inventoryItemRepository.findAllByProductIdIn(any()))
                .thenReturn(List.of(inventoryItem));

        ReservationResult result = reservationService.reserve(command(3));

        assertThat(result.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(result.failureCode()).isNull();
        assertThat(inventoryItem.getReservedQuantity()).isEqualTo(3);
    }

    @Test
    void existingReservationIsReturnedWithoutProcessingStockAgain() {
        CreateReservationCommand command = command(2);
        ReservationEntity existing = existingReservation(command);
        existing.markReserved(NOW);

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(existing));

        ReservationResult result = reservationService.reserve(command);

        assertThat(result.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(result.failureCode()).isNull();
        verifyNoInteractions(productRepository, inventoryItemRepository);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void existingFailedReservationReturnsOriginalFailureCode() {
        CreateReservationCommand command = command(2);
        ReservationEntity existing = existingReservation(command);
        existing.markFailed(ReservationFailureCode.PRODUCT_INACTIVE, NOW);

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(existing));

        ReservationResult result = reservationService.reserve(command);

        assertThat(result.status()).isEqualTo(ReservationStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo(ReservationFailureCode.PRODUCT_INACTIVE);
        assertThat(result.expiresAt()).isNull();
        verifyNoInteractions(productRepository, inventoryItemRepository);
        verify(reservationRepository, never()).save(any());
    }

    private CreateReservationCommand command(int quantity) {
        return new CreateReservationCommand(
                ORDER_ID,
                USER_ID,
                List.of(new ReservationItemCommand(PRODUCT_ID, quantity))
        );
    }

    private ReservationEntity existingReservation(
            CreateReservationCommand command
    ) {
        ReservationEntity reservation = ReservationEntity.create(
                ORDER_ID,
                USER_ID,
                NOW.plusSeconds(3600),
                NOW
        );

        command.items().forEach(item ->
                reservation.addItem(
                        item.productId(),
                        item.quantity()
                )
        );

        return reservation;
    }

    private ProductEntity productWithStatus(ProductStatus status) {
        ProductEntity product = mock(ProductEntity.class);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(product.getStatus()).thenReturn(status);
        return product;
    }
}
