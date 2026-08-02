package com.valentin.orderservice.logic;

import com.valentin.orderservice.client.InventoryClient;
import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.db.OutboxEventRepository;
import com.valentin.orderservice.domain.*;
import com.valentin.orderservice.domain.dictionary.OrderChangeHistoryReason;
import com.valentin.orderservice.domain.dictionary.OrderStatus;
import com.valentin.orderservice.domain.dictionary.ProductStatus;
import com.valentin.orderservice.domain.event.OrderCreatedEvent;
import com.valentin.orderservice.dto.*;
import com.valentin.orderservice.exception.MixedOrderCurrenciesException;
import com.valentin.orderservice.exception.OrderNotFoundException;
import com.valentin.orderservice.exception.ProductNotFoundException;
import com.valentin.orderservice.exception.ProductUnavailableException;
import com.valentin.orderservice.mapper.OrderMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@Service
public class OrderService {
    private final OrderMapper mapper;
    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;
    private final InventoryClient inventoryClient;
    private final Clock clock;


    @Transactional
    public OrderResponse createOrder(CreateOrderRequest orderRequest) {

        Set<UUID> productIds = orderRequest.items().stream()
                .map(CreateOrderItemRequest::productId)
                .collect(Collectors.toSet());

        List<ProductSnapshot> products =
                inventoryClient.getProductsSnapshot(new ProductsBatchRequest(productIds));

        Map<UUID, ProductSnapshot> productsById = products.stream()
                .collect(Collectors.toMap(
                        ProductSnapshot::productId,
                        Function.identity()
                ));

        Set<UUID> missingProductIds = new HashSet<>(productIds);
        missingProductIds.removeAll(productsById.keySet());

        if (!missingProductIds.isEmpty()) {
            throw new ProductNotFoundException(missingProductIds);
        }

        Set<UUID> unavailableProductIds = products.stream()
                .filter(product -> !ProductStatus.ACTIVE.equals(product.status()))
                .map(ProductSnapshot::productId)
                .collect(Collectors.toSet());

        if (!unavailableProductIds.isEmpty()) {
            throw new ProductUnavailableException(unavailableProductIds);
        }

        Set<String> currencies = products.stream()
                .map(ProductSnapshot::currency)
                .collect(Collectors.toSet());

        if (currencies.size() != 1) {
            throw new MixedOrderCurrenciesException(currencies);
        }

        String orderCurrency = currencies.iterator().next();

        Instant timeNow = clock.instant();

        OrderEntity order = OrderEntity.createOrderEntity(
                orderRequest.userId(),
                new ArrayList<>(),
                OrderStatus.WAITING_FOR_INVENTORY,
                orderCurrency,
                timeNow
        );

        List<CreateOrderItemRequest> itemsRequest = orderRequest.items();

        for (CreateOrderItemRequest item : itemsRequest) {
            ProductSnapshot product = productsById.get(item.productId());

            OrderItemEntity orderItemEntity = OrderItemEntity.create(
                    product.productId(),
                    product.productName(),
                    product.unitPrice(),
                    product.currency(),
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

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {

        return mapper.toOrderResponse(findOrder(id));
    }


    @Transactional(readOnly = true)
    public List<OrderHistoryResponse> getOrderHistoryById(UUID orderId) {

        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException("Order history not found id = " + orderId);
        }

        List<OrderHistoryEntity> orderHistories = orderHistoryRepository
                .findOrderHistoryByIdByCreatedTimeAsc(orderId);

        return mapper.toOrderHistoryResponseList(orderHistories);
    }

    @Transactional(readOnly = true)
    public OrderSummaryResponseList getOrdersByUserId(UUID userId) {
        List<OrderEntity> orders = orderRepository.findByUserIdOrderByCreatedAtAsc(userId);

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (OrderEntity order : orders) {
            totalPrice = totalPrice.add(order.getTotalPrice());
        }

        List<OrderSummaryResponse> orderSummaryResponses = mapper.toOrderSummaryResponses(orders);

        return new OrderSummaryResponseList(
                totalPrice,
                orderSummaryResponses
        );
    }

    private OrderEntity findOrder(UUID id) {
        return orderRepository.findById(id).orElseThrow(() ->
                new OrderNotFoundException("Order not found id = " + id)
        );
    }
}
