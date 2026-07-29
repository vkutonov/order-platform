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
import com.valentin.inventoryservice.exception.InvalidReservationStatusException;
import com.valentin.inventoryservice.exception.ReservationCommandConflictException;
import com.valentin.inventoryservice.exception.ReservationNotExpiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void existingReservationWithDifferentPayloadIsRejected() {
        ReservationEntity existing = existingReservation(command(2));
        existing.markReserved(NOW);

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> reservationService.reserve(command(3)))
                .isInstanceOf(ReservationCommandConflictException.class);

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

    @Test
    void commitMovesReservedStockToSoldStock() {
        ReservationEntity reservation = existingReservation(command(3));
        reservation.markReserved(NOW.minusSeconds(60));

        InventoryItemEntity inventoryItem =
                InventoryItemEntity.create(PRODUCT_ID, 5, NOW.minusSeconds(120));
        inventoryItem.reserve(3, NOW.minusSeconds(60));

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(reservation));
        when(inventoryItemRepository.findAllByProductIdIn(any()))
                .thenReturn(List.of(inventoryItem));

        reservationService.commit(ORDER_ID);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(inventoryItem.getQuantityOnHand()).isEqualTo(2);
        assertThat(inventoryItem.getReservedQuantity()).isZero();
    }

    @Test
    void releaseReturnsReservedStockToAvailableStock() {
        ReservationEntity reservation = existingReservation(command(3));
        reservation.markReserved(NOW.minusSeconds(60));

        InventoryItemEntity inventoryItem =
                InventoryItemEntity.create(PRODUCT_ID, 5, NOW.minusSeconds(120));
        inventoryItem.reserve(3, NOW.minusSeconds(60));

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(reservation));
        when(inventoryItemRepository.findAllByProductIdIn(any()))
                .thenReturn(List.of(inventoryItem));

        reservationService.release(ORDER_ID);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(inventoryItem.getQuantityOnHand()).isEqualTo(5);
        assertThat(inventoryItem.getReservedQuantity()).isZero();
    }

    @Test
    void expireReleasesStockAfterExpirationTime() {
        ReservationEntity reservation = reservationExpiringAt(
                NOW.minusSeconds(1),
                3
        );

        InventoryItemEntity inventoryItem =
                InventoryItemEntity.create(PRODUCT_ID, 5, NOW.minusSeconds(120));
        inventoryItem.reserve(3, NOW.minusSeconds(60));

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(reservation));
        when(inventoryItemRepository.findAllByProductIdIn(any()))
                .thenReturn(List.of(inventoryItem));

        reservationService.expire(ORDER_ID);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(inventoryItem.getQuantityOnHand()).isEqualTo(5);
        assertThat(inventoryItem.getReservedQuantity()).isZero();
    }

    @Test
    void expireRejectsReservationBeforeExpirationTime() {
        ReservationEntity reservation = reservationExpiringAt(
                NOW.plusSeconds(1),
                3
        );

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.expire(ORDER_ID))
                .isInstanceOf(ReservationNotExpiredException.class);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void repeatedCommitDoesNotProcessStockAgain() {
        ReservationEntity reservation = existingReservation(command(3));
        reservation.markReserved(NOW.minusSeconds(60));
        reservation.commit(NOW);

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(reservation));

        reservationService.commit(ORDER_ID);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void repeatedReleaseDoesNotProcessStockAgain() {
        ReservationEntity reservation = existingReservation(command(3));
        reservation.markReserved(NOW.minusSeconds(60));
        reservation.release(NOW);

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(reservation));

        reservationService.release(ORDER_ID);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void repeatedExpireDoesNotProcessStockAgain() {
        ReservationEntity reservation = reservationExpiringAt(
                NOW.minusSeconds(1),
                3
        );
        reservation.expire(NOW);

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(reservation));

        reservationService.expire(ORDER_ID);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void releaseAfterCommitIsRejectedWithoutProcessingStock() {
        ReservationEntity reservation = existingReservation(command(3));
        reservation.markReserved(NOW.minusSeconds(60));
        reservation.commit(NOW);

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.release(ORDER_ID))
                .isInstanceOf(InvalidReservationStatusException.class);

        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void commitAfterReleaseIsRejectedWithoutProcessingStock() {
        ReservationEntity reservation = existingReservation(command(3));
        reservation.markReserved(NOW.minusSeconds(60));
        reservation.release(NOW);

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.commit(ORDER_ID))
                .isInstanceOf(InvalidReservationStatusException.class);

        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void commitAfterExpireIsRejectedWithoutProcessingStock() {
        ReservationEntity reservation = reservationExpiringAt(
                NOW.minusSeconds(1),
                3
        );
        reservation.expire(NOW);

        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.commit(ORDER_ID))
                .isInstanceOf(InvalidReservationStatusException.class);

        verifyNoInteractions(inventoryItemRepository);
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

    private ReservationEntity reservationExpiringAt(
            Instant expiresAt,
            int quantity
    ) {
        ReservationEntity reservation = ReservationEntity.create(
                ORDER_ID,
                USER_ID,
                expiresAt,
                NOW.minusSeconds(120)
        );

        reservation.addItem(PRODUCT_ID, quantity);
        reservation.markReserved(NOW.minusSeconds(60));

        return reservation;
    }

    private ProductEntity productWithStatus(ProductStatus status) {
        ProductEntity product = mock(ProductEntity.class);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(product.getStatus()).thenReturn(status);
        return product;
    }
}
