package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.domain.*;
import com.valentin.orderservice.dto.CreateOrderItemRequest;
import com.valentin.orderservice.dto.CreateOrderRequest;
import com.valentin.orderservice.dto.OrderHistoryResponse;
import com.valentin.orderservice.dto.OrderResponse;
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


    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {

        return mapper.toOrderResponse(orderRepository.findById(orderId).orElseThrow( () ->
                new OrderNotFoundException("Order not found id = " + orderId))
        );
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
}
