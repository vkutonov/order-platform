package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.domain.*;
import com.valentin.orderservice.dto.*;
import com.valentin.orderservice.exception.OrderInvalidStatusException;
import com.valentin.orderservice.exception.OrderNotFoundException;
import com.valentin.orderservice.mapper.OrderMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Service
public class OrderService {
    private final OrderMapper mapper;
    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;


    @Transactional
    public OrderResponse createOrder(CreateOrderRequest orderRequest) {

        Instant instantNow = Instant.now();

        OrderEntity order = OrderEntity.createOrderEntity(
                orderRequest.userId(),
                new ArrayList<>(),
                OrderStatus.WAITING_FOR_INVENTORY,
                new BigDecimal("0.00"),
                "RUB"
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

        order.setTotalPrice(order.recalculateTotalPrice());
        orderRepository.save(order);

        OrderHistoryEntity orderStatusHistory = OrderHistoryEntity.create(
                order,
                null,
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderChangeHistoryReason.ORDER_CREATED,
                instantNow);

        orderHistoryRepository.save(orderStatusHistory);

        return mapper.toOrderResponse(order);
    }

    @Transactional
    public void reserveInventory(UUID id) {
        OrderEntity order = findOrder(id);

        if (order.getStatus() != OrderStatus.WAITING_FOR_INVENTORY) {
            throw new OrderInvalidStatusException("Order status must be WAITING_FOR_INVENTORY id = " + id);
        }

        changeStatus(
                order,
                OrderStatus.WAITING_FOR_PAYMENT,
                OrderChangeHistoryReason.INVENTORY_RESERVED
        );
    }


    private void changeStatus(
            OrderEntity order,
            OrderStatus status,
            OrderChangeHistoryReason reason
    ) {
        Instant timeNow = Instant.now();

        OrderHistoryEntity history = OrderHistoryEntity.create(
                order,
                order.getStatus(),
                status,
                reason,
                timeNow
        );

        order.setStatus(status);
        order.setUpdatedAt(timeNow);

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
