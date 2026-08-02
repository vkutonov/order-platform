package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.OrderHistoryEntity;
import com.valentin.orderservice.dto.OrderHistoryResponse;
import com.valentin.orderservice.dto.OrderResponse;
import com.valentin.orderservice.dto.OrderSummaryResponse;
import com.valentin.orderservice.dto.OrderSummaryResponseList;
import com.valentin.orderservice.exception.OrderNotFoundException;
import com.valentin.orderservice.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderMapper mapper;

    private final OrderRepository orderRepository;

    private final OrderHistoryRepository orderHistoryRepository;


    public OrderResponse getOrderById(UUID id) {
        return mapper.toOrderResponse(findOrder(id));
    }


    public List<OrderHistoryResponse> getOrderHistoryById(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException("Order history not found id = " + orderId);
        }

        List<OrderHistoryEntity> orderHistories = orderHistoryRepository
                .findOrderHistoryByIdByCreatedTimeAsc(orderId);

        return mapper.toOrderHistoryResponseList(orderHistories);
    }


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
