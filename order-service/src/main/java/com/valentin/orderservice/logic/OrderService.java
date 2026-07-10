package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.domain.*;
import com.valentin.orderservice.domain.dictionary.OrderChangeHistoryReason;
import com.valentin.orderservice.domain.dictionary.OrderStatus;
import com.valentin.orderservice.dto.*;
import com.valentin.orderservice.exception.OrderNotFoundException;
import com.valentin.orderservice.mapper.OrderMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@AllArgsConstructor
@Service
public class OrderService {
    private final OrderMapper mapper;
    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;


    @Transactional
    public OrderResponse createOrder(CreateOrderRequest orderRequest) {

        Instant timeNow = Instant.now();

        OrderEntity order = OrderEntity.createOrderEntity(
                orderRequest.userId(),
                new ArrayList<>(),
                OrderStatus.WAITING_FOR_INVENTORY,
                "RUB",
                timeNow
        );

        List<CreateOrderItemRequest> itemsRequest = orderRequest.items();

        for (CreateOrderItemRequest item : itemsRequest) {
            OrderItemEntity orderItemEntity = OrderItemEntity.create(
                    item.productId(),
                    item.productName(),
                    item.unitPrice(),
                    item.quantity()
            );

            order.addItem(orderItemEntity);
        }

        orderRepository.save(order);

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

        return mapper.toOrderResponse(order);
    }

    @Transactional
    public void reserveInventory(UUID orderId) {
        OrderEntity order = findOrder(orderId);
        Instant timeNow = Instant.now();

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
        Instant timeNow = Instant.now();

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
        Instant timeNow = Instant.now();

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
        Instant timeNow = Instant.now();

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
        Instant timeNow = Instant.now();

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
