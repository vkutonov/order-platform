package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.OrderStatusHistoryRepository;
import com.valentin.orderservice.domain.*;
import com.valentin.orderservice.dto.CreateOrderItemRequest;
import com.valentin.orderservice.dto.CreateOrderRequest;
import com.valentin.orderservice.dto.OrderResponse;
import com.valentin.orderservice.exception.OrderNotFoundException;
import com.valentin.orderservice.mapper.OrderMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Service
public class OrderService {
    private final OrderMapper mapper;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest orderRequest) {

        OrderEntity order = new OrderEntity();
        Instant instantNow = Instant.now();
        order.setCreatedAt(instantNow);
        order.setUpdatedAt(instantNow);
        order.setUserId(orderRequest.userId());
        order.setCurrency("RUB");
        order.setStatus(OrderStatus.WAITING_FOR_INVENTORY);

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

        OrderStatusHistoryEntity orderStatusHistory = OrderStatusHistoryEntity.create(
                order,
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderChangeHistoryReason.ORDER_CREATED,
                instantNow
        );

        orderStatusHistoryRepository.save(orderStatusHistory);

        return mapper.toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {

        return mapper.toOrderResponse(orderRepository.findById(orderId).orElseThrow( () ->
                new OrderNotFoundException("Order not found id = " + orderId))
        );
    }
}
