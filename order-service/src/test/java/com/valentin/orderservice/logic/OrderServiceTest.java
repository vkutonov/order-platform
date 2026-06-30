package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.domain.OrderChangeHistoryReason;
import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.OrderHistoryEntity;
import com.valentin.orderservice.domain.OrderStatus;
import com.valentin.orderservice.dto.*;
import com.valentin.orderservice.exception.OrderNotFoundException;
import com.valentin.orderservice.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderService orderService;

    @Captor
    private ArgumentCaptor<OrderEntity> orderCaptor;

    @Captor
    private ArgumentCaptor<OrderHistoryEntity> historyCaptor;

    @Test
    void createOrder_shouldSaveOrderWithInitialStatusAndHistory() {

        CreateOrderRequest orderRequest = createValidRequest();
        OrderResponse orderResponse = createOrderResponse();

        when(orderRepository.save(any(OrderEntity.class)))
                .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        when(orderMapper.toOrderResponse(any(OrderEntity.class)))
                .thenReturn(orderResponse);

        OrderResponse result = orderService.createOrder(orderRequest);

        assertThat(result).isSameAs(orderResponse);

        verify(orderRepository).save(orderCaptor.capture());
        verify(orderHistoryRepository).save(historyCaptor.capture());

        OrderEntity order = orderCaptor.getValue();

        verify(orderMapper).toOrderResponse(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_FOR_INVENTORY);
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isEqualTo(order.getCreatedAt());
        assertThat(order.getOrderItems()).hasSize(1);
        assertThat(order.getOrderItems().getFirst().getProductName()).isEqualTo("Bicycle");
        assertThat(order.getOrderItems().getFirst().getUnitPrice()).isEqualByComparingTo("444.44");
        assertThat(order.getOrderItems().getFirst().getQuantity()).isEqualTo(2);
        assertThat(order.getOrderItems().getFirst().getTotalPrice()).isEqualByComparingTo("888.88");
        assertThat(order.getOrderItems().getFirst().getOrder()).isSameAs(order);

        OrderHistoryEntity history = historyCaptor.getValue();

        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getNewStatus()).isEqualTo(OrderStatus.WAITING_FOR_INVENTORY);
        assertThat(history.getOldStatus()).isNull();
        assertThat(history.getReason()).isEqualTo(OrderChangeHistoryReason.ORDER_CREATED);
        assertThat(history.getCreatedAt()).isEqualTo(order.getCreatedAt());

    }

    @Test
    void createOrder_withMultipleItems_shouldSetTotalPriceFromAllItems() {

        CreateOrderRequest orderRequest = createValidRequestWithMultipleItems();
        OrderResponse orderResponse = createOrderResponse();

        when(orderRepository.save(any(OrderEntity.class)))
                .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        when(orderMapper.toOrderResponse(any(OrderEntity.class)))
                .thenReturn(orderResponse);

        OrderResponse result = orderService.createOrder(orderRequest);

        assertThat(result).isSameAs(orderResponse);

        verify(orderRepository).save(orderCaptor.capture());
        verify(orderHistoryRepository).save(any(OrderHistoryEntity.class));

        OrderEntity order = orderCaptor.getValue();

        assertThat(order.getOrderItems()).hasSize(2);
        assertThat(order.getTotalPrice()).isEqualByComparingTo("46.00");
    }


    @Test
    void getOrderById_existingOrder_shouldReturnMappedResponse() {
        UUID orderId = UUID.randomUUID();

        OrderEntity order = new OrderEntity();
        OrderResponse response = createOrderResponse();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderMapper.toOrderResponse(order)).thenReturn(response);

        OrderResponse result = orderService.getOrderById(orderId);

        assertThat(result).isSameAs(response);

        verify(orderRepository).findById(orderId);
        verify(orderMapper).toOrderResponse(order);
    }


    @Test
    void getOrderById_missingOrder_shouldThrowOrderNotFoundException() {
        UUID orderId = UUID.randomUUID();

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(orderId))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository).findById(orderId);
        verifyNoInteractions(orderMapper);
    }

    @Test
    void getOrderHistoryById_existingOrder_shouldReturnMappedResponse() {
        UUID orderId = UUID.randomUUID();

        List<OrderHistoryResponse> response = createOrderHistoryResponse();
        List<OrderHistoryEntity> history = List.of(new OrderHistoryEntity());

        when(orderRepository.existsById(orderId)).thenReturn(true);
        when(orderHistoryRepository.findOrderHistoryByIdByCreatedTimeAsc(orderId)).thenReturn(history);
        when(orderMapper.toOrderHistoryResponseList(history)).thenReturn(response);

        List<OrderHistoryResponse> result = orderService.getOrderHistoryById(orderId);

        assertThat(result).isSameAs(response);

        verify(orderRepository).existsById(orderId);
        verify(orderHistoryRepository).findOrderHistoryByIdByCreatedTimeAsc(orderId);
    }


    @Test
    void getOrderHistoryById_missingOrder_shouldThrowOrderNotFoundException() {
        UUID orderId = UUID.randomUUID();

        when(orderRepository.existsById(orderId)).thenReturn(false);

        assertThatThrownBy(() -> orderService.getOrderHistoryById(orderId))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository).existsById(orderId);
        verifyNoInteractions(orderHistoryRepository);
        verifyNoInteractions(orderMapper);
    }

    private List<OrderHistoryResponse> createOrderHistoryResponse() {
        OrderHistoryResponse historyItem1 = new OrderHistoryResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                OrderStatus.WAITING_FOR_INVENTORY,
                "test reason",
                Instant.now()
        );

        OrderHistoryResponse historyItem2 = new OrderHistoryResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderStatus.WAITING_FOR_PAYMENT,
                "test reason2",
                Instant.now()
        );

        OrderHistoryResponse historyItem3 = new OrderHistoryResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OrderStatus.WAITING_FOR_PAYMENT,
                OrderStatus.PAYMENT_FAILED,
                "test reason3",
                Instant.now()
        );

        return List.of(historyItem1, historyItem2, historyItem3);
    }


    private OrderResponse createOrderResponse() {
        OrderItemResponse item = new OrderItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Bicycle",
                new BigDecimal("444.44"),
                2,
                new BigDecimal("888.88")
        );

        return new OrderResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OrderStatus.WAITING_FOR_INVENTORY,
                item.totalPrice(),
                "RUB",
                List.of(item),
                Instant.now(),
                Instant.now()
        );
    }


    private CreateOrderRequest createValidRequest() {
        CreateOrderItemRequest item1 = new CreateOrderItemRequest(
                UUID.randomUUID(),
                "Bicycle",
                new BigDecimal("444.44"),
                2
        );

        return new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(item1)
        );
    }

    private CreateOrderRequest createValidRequestWithMultipleItems() {
        CreateOrderItemRequest item1 = new CreateOrderItemRequest(
                UUID.randomUUID(),
                "Keyboard",
                new BigDecimal("10.50"),
                2
        );

        CreateOrderItemRequest item2 = new CreateOrderItemRequest(
                UUID.randomUUID(),
                "Mouse",
                new BigDecimal("25.00"),
                1
        );

        return new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(item1, item2)
        );
    }
}
