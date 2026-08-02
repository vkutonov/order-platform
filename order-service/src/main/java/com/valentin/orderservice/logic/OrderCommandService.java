package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.OutboxEventRepository;
import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.OrderHistoryEntity;
import com.valentin.orderservice.domain.OrderItemEntity;
import com.valentin.orderservice.domain.OutboxEventEntity;
import com.valentin.orderservice.domain.dictionary.OrderChangeHistoryReason;
import com.valentin.orderservice.domain.dictionary.OrderStatus;
import com.valentin.orderservice.domain.event.OrderCreatedEvent;
import com.valentin.orderservice.dto.*;
import com.valentin.orderservice.exception.OrderNotFoundException;
import com.valentin.orderservice.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final OrderHistoryRepository orderHistoryRepository;

    private final Clock clock;

    private final OrderRepository orderRepository;

    private final OrderMapper mapper;

    private final ObjectMapper objectMapper;

    private final OutboxEventRepository outboxEventRepository;


    @Transactional
    public OrderResponse persistOrder(
            PreparedOrderData preparedOrderData
    ){
        Instant timeNow = clock.instant();


        OrderEntity order = OrderEntity.createOrderEntity(
                preparedOrderData.userId(),
                new ArrayList<>(),
                OrderStatus.WAITING_FOR_INVENTORY,
                preparedOrderData.currency(),
                timeNow
        );


        for (PreparedOrderItem item : preparedOrderData.items()) {

            OrderItemEntity orderItemEntity = OrderItemEntity.create(
                    item.productId(),
                    item.productName(),
                    item.unitPrice(),
                    preparedOrderData.currency(),
                    item.quantity()
            );

            order.addItem(orderItemEntity);
        }

        OrderEntity saved = orderRepository.save(order);

        log.info(
                "Order created: orderId={}, status={}",
                order.getId(),
                order.getStatus()
        );

        OrderHistoryEntity orderStatusHistory = OrderHistoryEntity.create(
                order,
                null,
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderChangeHistoryReason.ORDER_CREATED,
                timeNow);

        orderHistoryRepository.save(orderStatusHistory);

        OrderCreatedEvent orderCreatedEvent = OrderCreatedEvent.of(
                saved.getId(),
                order.getUserId(),
                mapper.toItemsPayload(order.getOrderItems()),
                Map.of("source", "order-service"),
                timeNow
        );


        OutboxEventEntity outboxEvent = OutboxEventEntity.create(
                "Order",
                saved.getId(),
                "OrderCreatedEvent",
                objectMapper.writeValueAsString(orderCreatedEvent),
                timeNow
        );

        outboxEventRepository.save(outboxEvent);

        log.info(
                "Outbox event created: eventId={}, aggregateType={}, aggregateId={}, eventType={}, status={}",
                outboxEvent.getId(),
                outboxEvent.getAggregateType(),
                outboxEvent.getAggregateId(),
                outboxEvent.getEventType(),
                outboxEvent.getStatus()
        );

        return mapper.toOrderResponse(order);
    }

    @Transactional
    public void reserveInventory(UUID orderId) {
        OrderEntity order = findOrder(orderId);
        Instant timeNow = clock.instant();

        OrderStatus oldStatus = order.getStatus();

        order.markInventoryReserved(timeNow);

        saveStatusHistory(
                order,
                oldStatus,
                order.getStatus(),
                OrderChangeHistoryReason.INVENTORY_RESERVED,
                timeNow
        );

        log.info(
                "Order inventory reserved: orderId={}, oldStatus={}, newStatus={}",
                orderId,
                oldStatus,
                order.getStatus()
        );

    }

    @Transactional
    public void inventoryReservationFailed(UUID orderId) {
        OrderEntity order = findOrder(orderId);
        Instant timeNow = clock.instant();

        OrderStatus oldStatus = order.getStatus();

        order.markInventoryReservationFailed(timeNow);

        saveStatusHistory(
                order,
                oldStatus,
                order.getStatus(),
                OrderChangeHistoryReason.INVENTORY_RESERVATION_FAILED,
                timeNow
        );

        log.info(
                "Order inventory reservation failed: orderId={}, oldStatus={}, newStatus={}",
                orderId,
                oldStatus,
                order.getStatus()
        );

    }


    @Transactional
    public void markPaymentSucceeded(UUID orderId) {
        OrderEntity order = findOrder(orderId);

        OrderStatus oldStatus = order.getStatus();
        Instant timeNow = clock.instant();

        order.markPaymentSucceeded(timeNow);

        saveStatusHistory(
                order,
                oldStatus,
                order.getStatus(),
                OrderChangeHistoryReason.PAYMENT_SUCCEEDED,
                timeNow
        );

        log.info(
                "Order payment succeeded: orderId={}, oldStatus={}, newStatus={}",
                orderId,
                oldStatus,
                order.getStatus()
        );
    }

    @Transactional
    public void markPaymentFailed(UUID orderId) {
        OrderEntity order = findOrder(orderId);

        OrderStatus oldStatus = order.getStatus();
        Instant timeNow = clock.instant();

        order.markPaymentFailed(timeNow);

        saveStatusHistory(
                order,
                oldStatus,
                order.getStatus(),
                OrderChangeHistoryReason.PAYMENT_FAILED,
                timeNow
        );

        log.info(
                "Order payment failed: orderId={}, oldStatus={}, newStatus={}",
                orderId,
                oldStatus,
                order.getStatus()
        );
    }

    @Transactional
    public void cancelOrder(UUID orderId) {
        OrderEntity order = findOrder(orderId);

        OrderStatus oldStatus = order.getStatus();
        Instant timeNow = clock.instant();

        order.cancel(timeNow);

        saveStatusHistory(
                order,
                oldStatus,
                order.getStatus(),
                OrderChangeHistoryReason.ORDER_CANCELLED_BY_USER,
                timeNow
        );

        log.info(
                "Order cancelled: orderId={}, oldStatus={}, newStatus={}",
                orderId,
                oldStatus,
                order.getStatus()
        );

    }


    private void saveStatusHistory(
            OrderEntity order,
            OrderStatus oldStatus,
            OrderStatus newStatus,
            OrderChangeHistoryReason reason,
            Instant createdAt
    ) {
        OrderHistoryEntity history = OrderHistoryEntity.create(
                order,
                oldStatus,
                newStatus,
                reason,
                createdAt
        );

        orderHistoryRepository.save(history);
    }


    private OrderEntity findOrder(UUID id) {
        return orderRepository.findById(id).orElseThrow(() ->
                new OrderNotFoundException("Order not found id = " + id)
        );
    }
}
