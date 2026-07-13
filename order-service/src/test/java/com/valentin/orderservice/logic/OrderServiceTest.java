package com.valentin.orderservice.logic;

import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.db.OutboxEventRepository;
import com.valentin.orderservice.domain.OutboxEventEntity;
import com.valentin.orderservice.domain.dictionary.OrderChangeHistoryReason;
import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.OrderHistoryEntity;
import com.valentin.orderservice.domain.OrderItemEntity;
import com.valentin.orderservice.domain.dictionary.OrderStatus;
import com.valentin.orderservice.domain.dictionary.OutboxEventStatus;
import com.valentin.orderservice.domain.event.OrderCreatedEvent;
import com.valentin.orderservice.dto.*;
import com.valentin.orderservice.exception.InvalidOrderStatusTransitionException;
import com.valentin.orderservice.exception.OrderNotFoundException;
import com.valentin.orderservice.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ObjectMapper objectMapper;

    private OrderService orderService;

    private Clock clock;

    @Captor
    private ArgumentCaptor<OrderEntity> orderCaptor;

    @Captor
    private ArgumentCaptor<OrderHistoryEntity> historyCaptor;

    @Captor
    private ArgumentCaptor<OutboxEventEntity> outboxEventCaptor;

    @Captor
    private ArgumentCaptor<OrderCreatedEvent> orderCreatedEventCaptor;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        orderService = new OrderService(
                orderMapper,
                orderRepository,
                orderHistoryRepository,
                objectMapper,
                outboxEventRepository,
                clock
        );
    }

    @Test
    void createOrder_shouldCreateOrderWithCreatedStatus() {

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
    void createOrder_shouldCreateOutboxEvent(){

        Instant timeNow = NOW;

        CreateOrderRequest request = createValidRequestWithMultipleItems();

        OrderEntity order = OrderEntity.createOrderEntity(
                request.userId(),
                new ArrayList<>(),
                OrderStatus.WAITING_FOR_INVENTORY,
                "RUB",
                timeNow
        );

        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());

        String payload = """
            {
              "eventId": "11111111-1111-1111-1111-111111111111",
              "orderId": "%s",
              "context": {
                "source": "order-service"
              },
              "occurredAt": "%s"
            }
            """.formatted(order.getId(), timeNow);

        when(orderRepository.save(any(OrderEntity.class)))
                .thenReturn(order);
        when(objectMapper.writeValueAsString(any(OrderCreatedEvent.class)))
                .thenReturn(payload);

        orderService.createOrder(request);

        verify(outboxEventRepository).save(outboxEventCaptor.capture());

        OutboxEventEntity outboxEvent = outboxEventCaptor.getValue();

        assertThat(outboxEvent.getAggregateType()).isSameAs("Order");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(order.getId());
        assertThat(outboxEvent.getEventType()).isEqualTo("OrderCreatedEvent");
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(outboxEvent.getPayload()).contains(order.getId().toString());
        assertThat(outboxEvent.getCreatedAt()).isAfterOrEqualTo(timeNow);

        verify(objectMapper).writeValueAsString(orderCreatedEventCaptor.capture());

        OrderCreatedEvent event = orderCreatedEventCaptor.getValue();

        assertThat(event.orderId()).isEqualTo(order.getId());
        assertThat(event.context()).containsEntry("source", "order-service");
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.eventId()).isNotNull();
    }

    @Test
    void getOrderById_existingOrder_shouldReturnMappedResponse() {
        UUID orderId = UUID.randomUUID();

        OrderEntity order = orderWithStatus(OrderStatus.WAITING_FOR_INVENTORY);
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
    void getOrdersByUserId_existingOrders_shouldReturnMappedResponses() {
        UUID userId = UUID.randomUUID();
        Instant timeNow = NOW;

        OrderEntity entity = orderWithStatus(OrderStatus.WAITING_FOR_INVENTORY);
        entity.addItem(OrderItemEntity.create(
                UUID.randomUUID(),
                "Desk",
                new BigDecimal("100.00"),
                1
        ));

        OrderSummaryResponse response = new OrderSummaryResponse(
                entity.getId(),
                userId,
                OrderStatus.WAITING_FOR_INVENTORY,
                new BigDecimal("100.00"),
                "RUB",
                timeNow,
                timeNow
        );

        List<OrderEntity> entities = List.of(entity);
        List<OrderSummaryResponse> responses = List.of(response);

        when(orderRepository.findByUserIdOrderByCreatedAtAsc(userId))
                .thenReturn(entities);

        when(orderMapper.toOrderSummaryResponses(entities))
                .thenReturn(responses);

        OrderSummaryResponseList result = orderService.getOrdersByUserId(userId);

        assertThat(result.totalPrice()).isEqualByComparingTo("100.00");
        assertThat(result.orderSummaryResponses()).containsExactly(response);

        verify(orderRepository).findByUserIdOrderByCreatedAtAsc(userId);
        verify(orderMapper).toOrderSummaryResponses(entities);
    }


    @Test
    void getOrdersByUserId_noOrders_shouldReturnEmptyList() {
        UUID userId = UUID.randomUUID();

        List<OrderEntity> entities = List.of();
        List<OrderSummaryResponse> responses = List.of();

        when(orderRepository.findByUserIdOrderByCreatedAtAsc(userId))
                .thenReturn(entities);

        when(orderMapper.toOrderSummaryResponses(entities))
                .thenReturn(responses);

        OrderSummaryResponseList result = orderService.getOrdersByUserId(userId);

        assertThat(result.totalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.orderSummaryResponses()).isEmpty();

        verify(orderRepository).findByUserIdOrderByCreatedAtAsc(userId);
        verify(orderMapper).toOrderSummaryResponses(entities);
    }

    @Test
    void getOrderHistoryById_existingOrder_shouldReturnMappedResponse() {
        UUID orderId = UUID.randomUUID();

        List<OrderHistoryResponse> response = createOrderHistoryResponse();
        List<OrderHistoryEntity> history = List.of(OrderHistoryEntity.create(
                orderWithStatus(OrderStatus.WAITING_FOR_INVENTORY),
                null,
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderChangeHistoryReason.ORDER_CREATED,
                NOW
        ));

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
                NOW
        );

        OrderHistoryResponse historyItem2 = new OrderHistoryResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderStatus.WAITING_FOR_PAYMENT,
                "test reason2",
                NOW
        );

        OrderHistoryResponse historyItem3 = new OrderHistoryResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OrderStatus.WAITING_FOR_PAYMENT,
                OrderStatus.PAYMENT_FAILED,
                "test reason3",
                NOW
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
                NOW,
                NOW
        );
    }


    @Test
    void reserveInventory_shouldChangeStatusAndSaveHistory() {
        OrderEntity order = orderWithStatus(OrderStatus.WAITING_FOR_INVENTORY);

        when(orderRepository.findById(order.getId()))
                .thenReturn(Optional.of(order));

        orderService.reserveInventory(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_FOR_PAYMENT);
        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(order.getCreatedAt());

        verify(orderRepository).findById(order.getId());
        verify(orderHistoryRepository).save(historyCaptor.capture());
        assertStatusHistory(
                historyCaptor.getValue(),
                order,
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderStatus.WAITING_FOR_PAYMENT,
                OrderChangeHistoryReason.INVENTORY_RESERVED
        );
    }

    @Test
    void markPaymentSucceeded_shouldChangeStatusAndSaveHistory() {
        OrderEntity order = orderWithStatus(OrderStatus.WAITING_FOR_PAYMENT);

        when(orderRepository.findById(order.getId()))
                .thenReturn(Optional.of(order));

        orderService.markPaymentSucceeded(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(order.getCreatedAt());

        verify(orderRepository).findById(order.getId());
        verify(orderHistoryRepository).save(historyCaptor.capture());
        assertStatusHistory(
                historyCaptor.getValue(),
                order,
                OrderStatus.WAITING_FOR_PAYMENT,
                OrderStatus.PAID,
                OrderChangeHistoryReason.PAYMENT_SUCCEEDED
        );
    }

    @Test
    void markPaymentFailed_shouldChangeStatusAndSaveHistory() {
        OrderEntity order = orderWithStatus(OrderStatus.WAITING_FOR_PAYMENT);

        when(orderRepository.findById(order.getId()))
                .thenReturn(Optional.of(order));

        orderService.markPaymentFailed(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(order.getCreatedAt());

        verify(orderRepository).findById(order.getId());
        verify(orderHistoryRepository).save(historyCaptor.capture());
        assertStatusHistory(
                historyCaptor.getValue(),
                order,
                OrderStatus.WAITING_FOR_PAYMENT,
                OrderStatus.PAYMENT_FAILED,
                OrderChangeHistoryReason.PAYMENT_FAILED
        );
    }

    @Test
    void cancelOrder_shouldChangeStatusAndSaveHistory() {
        OrderEntity order = orderWithStatus(OrderStatus.WAITING_FOR_INVENTORY);

        when(orderRepository.findById(order.getId()))
                .thenReturn(Optional.of(order));

        orderService.cancelOrder(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(order.getCreatedAt());

        verify(orderRepository).findById(order.getId());
        verify(orderHistoryRepository).save(historyCaptor.capture());
        assertStatusHistory(
                historyCaptor.getValue(),
                order,
                OrderStatus.WAITING_FOR_INVENTORY,
                OrderStatus.CANCELLED,
                OrderChangeHistoryReason.ORDER_CANCELLED_BY_USER
        );
    }

    @Test
    void invalidTransition_shouldThrowExceptionAndNotSaveHistory() {
        OrderEntity order = orderWithStatus(OrderStatus.WAITING_FOR_INVENTORY);

        when(orderRepository.findById(order.getId()))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.markPaymentSucceeded(order.getId()))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orderRepository).findById(order.getId());
        verifyNoInteractions(orderHistoryRepository);
    }

    @Test
    void reserveInventory_shouldThrowOrderNotFoundException() {
        UUID orderId = UUID.randomUUID();

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.reserveInventory(orderId))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository).findById(orderId);
        verifyNoInteractions(orderHistoryRepository);
    }

    private void assertStatusHistory(
            OrderHistoryEntity history,
            OrderEntity order,
            OrderStatus oldStatus,
            OrderStatus newStatus,
            OrderChangeHistoryReason reason
    ) {
        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getCreatedAt()).isNotNull();
        assertThat(history.getOldStatus()).isEqualTo(oldStatus);
        assertThat(history.getNewStatus()).isEqualTo(newStatus);
        assertThat(history.getReason()).isEqualTo(reason);
    }

    private OrderEntity orderWithStatus(OrderStatus status) {
        OrderEntity order = OrderEntity.createOrderEntity(
                UUID.randomUUID(),
                new ArrayList<>(),
                status,
                "RUB",
                NOW
        );
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());

        return order;
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
