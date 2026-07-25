package com.valentin.inventoryservice.logic;

import com.valentin.inventoryservice.db.InventoryItemRepository;
import com.valentin.inventoryservice.db.ProductRepository;
import com.valentin.inventoryservice.db.ReservationRepository;
import com.valentin.inventoryservice.domain.InventoryItemEntity;
import com.valentin.inventoryservice.domain.ProductEntity;
import com.valentin.inventoryservice.domain.ReservationEntity;
import com.valentin.inventoryservice.domain.ReservationItemEntity;
import com.valentin.inventoryservice.domain.dictionary.ProductStatus;
import com.valentin.inventoryservice.domain.dictionary.ReservationFailureCode;
import com.valentin.inventoryservice.domain.dictionary.ReservationStatus;
import com.valentin.inventoryservice.dto.CreateReservationCommand;
import com.valentin.inventoryservice.dto.ReservationItemCommand;
import com.valentin.inventoryservice.dto.ReservationResult;
import com.valentin.inventoryservice.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ProductRepository productRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final Clock clock;

    @Transactional
    public ReservationResult reserve(CreateReservationCommand command) {

        Optional<ReservationEntity> existing = reservationRepository.findByOrderId(
                command.orderId()
        );

        if (existing.isPresent()) {
            return toResult(existing.get());
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(5, ChronoUnit.HOURS);

        Set<UUID> productIds = command.items().stream()
                .map(ReservationItemCommand::productId)
                .collect(Collectors.toSet());

        Map<UUID, ProductEntity> products = loadProducts(productIds);

        ReservationEntity reservation = ReservationEntity.create(
                command.orderId(),
                command.userId(),
                expiresAt,
                now
        );

        for (ReservationItemCommand item: command.items()) {
            reservation.addItem(
                    item.productId(),
                    item.quantity()
            );
        }

        ReservationFailureCode failureCode = findProductFailure(
                command.items(),
                products
        );

        Map<UUID, InventoryItemEntity> inventoryItems = Map.of();

        if (failureCode == null) {
            inventoryItems = loadInventoryItems(productIds);
            failureCode = findStockFailure(
                    command.items(),
                    inventoryItems
            );
        }

        if (failureCode != null) {
            reservation.markFailed(
                    failureCode,
                    now
            );

            ReservationEntity saved = reservationRepository.save(reservation);

            return ReservationResult.failed(
                    saved.getId(),
                    saved.getOrderId(),
                    failureCode
            );
        }

        for (ReservationItemEntity reservationItem : reservation.getItems()) {
            InventoryItemEntity inventoryItem =
                    inventoryItems.get(reservationItem.getProductId());

            inventoryItem.reserve(
                    reservationItem.getQuantity(),
                    now
            );
        }

        reservation.markReserved(now);

        ReservationEntity saved = reservationRepository.save(reservation);

        log.info("Reservation saved: id={}, userId = {}, orderId={}, time={}",
                saved.getId(),
                saved.getUserId(),
                saved.getOrderId(),
                saved.getCreatedAt()
        );

        return ReservationResult.reserved(
                saved.getId(),
                saved.getOrderId(),
                saved.getExpiresAt()
        );

    }

    private Map<UUID, ProductEntity> loadProducts(Set<UUID> productIds) {
        Map<UUID, ProductEntity> productsById = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(
                        ProductEntity::getId,
                        product -> product
                ));

        Set<UUID> missingProductIds = new HashSet<>(productIds);
        missingProductIds.removeAll(productsById.keySet());

        if (!missingProductIds.isEmpty()) {
            throw new ProductNotFoundException(
                    "Products not found for productIds: " + missingProductIds
            );
        }

        return productsById;
    }

    private Map<UUID, InventoryItemEntity> loadInventoryItems(Set<UUID> productIds) {
        Map<UUID, InventoryItemEntity> inventoryByProductIds =
                inventoryItemRepository.findAllByProductIdIn(productIds)
                        .stream()
                        .collect(Collectors.toMap(
                                InventoryItemEntity::getProductId,
                                inventoryItem -> inventoryItem
                        ));

        Set<UUID> missingProductIds = new HashSet<>(productIds);
        missingProductIds.removeAll(inventoryByProductIds.keySet());

        if (!missingProductIds.isEmpty()) {
            throw new ProductNotFoundException(
                    "Inventory items not found for productIds: " + missingProductIds
            );
        }

        return inventoryByProductIds;
    }

    private ReservationFailureCode findProductFailure(
            List<ReservationItemCommand> reservationItems,
            Map<UUID, ProductEntity> products
    ) {
        for (ReservationItemCommand requestedItem : reservationItems) {
            ProductEntity product = products.get(requestedItem.productId());

            if (product.getStatus() != ProductStatus.ACTIVE) {
                log.warn(
                        "Product is not active: productId={}, status={}",
                        product.getId(),
                        product.getStatus()
                );

                return ReservationFailureCode.PRODUCT_INACTIVE;
            }
        }

        return null;
    }

    private ReservationFailureCode findStockFailure(
            List<ReservationItemCommand> reservationItems,
            Map<UUID, InventoryItemEntity> inventoryItems
    ) {
        for (ReservationItemCommand requestedItem : reservationItems) {

            InventoryItemEntity item = inventoryItems.get(requestedItem.productId());

            int available = item.getAvailableQuantity();

            if (available < requestedItem.quantity()) {
                log.warn(
                        "Insufficient stock: productId={}, requested={}, available={}",
                        requestedItem.productId(),
                        requestedItem.quantity(),
                        available
                );

                return ReservationFailureCode.INSUFFICIENT_STOCK;
            }
        }

        return null;
    }

    private ReservationResult toResult(
            ReservationEntity reservation
    ) {
        return new ReservationResult(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getStatus(),
                reservation.getStatus() == ReservationStatus.FAILED
                        ? null
                        : reservation.getExpiresAt(),
                reservation.getFailureCode()
        );
    }

    @Transactional
    public void commit(UUID orderId) {
        // подтверждает резерв всего заказа
    }

    @Transactional
    public void release(UUID orderId) {
        // освобождает резерв всего заказа
    }

    @Transactional
    public void expire(UUID orderId) {
        // освобождает истёкший резерв
    }

}
