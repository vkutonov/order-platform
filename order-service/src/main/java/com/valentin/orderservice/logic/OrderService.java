package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.domain.*;
import com.valentin.orderservice.dto.*;
import com.valentin.orderservice.exception.OrderInvalidStatusException;
import com.valentin.orderservice.exception.OrderNotFoundException;
import com.valentin.orderservice.mapper.OrderMapper;
import lombok.AllArgsConstructor;
import org.hibernate.query.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

        validateStatus(order, OrderStatus.WAITING_FOR_INVENTORY);

        changeStatus(
                order,
                OrderStatus.WAITING_FOR_PAYMENT,
                OrderChangeHistoryReason.INVENTORY_RESERVED
        );
    }

    @Transactional
    public void inventoryReservationFailed(UUID id) {
        OrderEntity order = findOrder(id);

        validateStatus(order, OrderStatus.WAITING_FOR_INVENTORY);

        changeStatus(
                order,
                OrderStatus.INVENTORY_RESERVATION_FAILED,
                OrderChangeHistoryReason.INVENTORY_RESERVATION_FAILED
        );

    }


    @Transactional
    public void confirmPayment(UUID id) {
        OrderEntity order = findOrder(id);

        validateStatus(order, OrderStatus.WAITING_FOR_PAYMENT);

        changeStatus(
                order,
                OrderStatus.PAID,
                OrderChangeHistoryReason.PAYMENT_SUCCEEDED
        );
    }

    @Transactional
    public void paymentFailed(UUID id) {
        OrderEntity order = findOrder(id);

        validateStatus(order, OrderStatus.WAITING_FOR_PAYMENT);

        changeStatus(
                order,
                OrderStatus.PAYMENT_FAILED,
                OrderChangeHistoryReason.PAYMENT_FAILED
        );
    }

    @Transactional
    public void cancelOrder(UUID id) {
        OrderEntity order = findOrder(id);

        validateStatusIn(
                order,
                Set.of(
                        OrderStatus.WAITING_FOR_INVENTORY,
                        OrderStatus.WAITING_FOR_PAYMENT,
                        OrderStatus.PAID
                )
        );

        changeStatus(
                order,
                OrderStatus.CANCELLED,
                OrderChangeHistoryReason.ORDER_CANCELLED_BY_USER
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

    // транзакция откатывается если выбрасывается Runtime исключение
    private void validateStatusIn(OrderEntity order, Set<OrderStatus> allowedStatuses) {
        if (!allowedStatuses.contains(order.getStatus())) {
            throw new OrderInvalidStatusException(
                    "Order status mustn't be in " + order.getStatus() + " status"
            );
        }
    }

    private void validateStatus(OrderEntity order, OrderStatus expectedStatus) {
        if (order.getStatus() != expectedStatus) {
            throw new OrderInvalidStatusException(
                    "Order status mustn't be in " + order.getStatus() + " status"
            );
        }
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
