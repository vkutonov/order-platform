package com.valentin.orderservice.logic;

import com.valentin.orderservice.client.InventoryClient;
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
import com.valentin.orderservice.domain.dictionary.ProductStatus;
import com.valentin.orderservice.domain.event.OrderCreatedEvent;
import com.valentin.orderservice.domain.event.OrderCreatedItem;
import com.valentin.orderservice.dto.*;
import com.valentin.orderservice.exception.InvalidOrderStatusTransitionException;
import com.valentin.orderservice.exception.MixedOrderCurrenciesException;
import com.valentin.orderservice.exception.OrderNotFoundException;
import com.valentin.orderservice.exception.ProductNotFoundException;
import com.valentin.orderservice.exception.ProductUnavailableException;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");
    private static final UUID BICYCLE_PRODUCT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID KEYBOARD_PRODUCT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MOUSE_PRODUCT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

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

    @Mock
    private InventoryClient inventoryClient;

    private OrderService orderService;

    private Clock clock;

    private OrderCreationValidator orderCreationValidator;

    @Captor
    private ArgumentCaptor<OrderEntity> orderCaptor;

    @Captor
    private ArgumentCaptor<OrderHistoryEntity> historyCaptor;

    @Captor
    private ArgumentCaptor<OutboxEventEntity> outboxEventCaptor;

    @Captor
    private ArgumentCaptor<OrderCreatedEvent> orderCreatedEventCaptor;

    @Captor
    private ArgumentCaptor<PreparedOrderData> preparedOrderDataCaptor;

    @Mock
    private OrderCommandService orderCommandService;

    private OrderCommandService actualOrderCommandService;

    private OrderQueryService orderQueryService;


    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);

        orderCreationValidator = new OrderCreationValidator();

        actualOrderCommandService = new OrderCommandService(
                orderHistoryRepository,
                clock,
                orderRepository,
                orderMapper,
                objectMapper,
                outboxEventRepository
        );

        orderQueryService = new OrderQueryService(
                orderMapper,
                orderRepository,
                orderHistoryRepository
        );

        orderService = new OrderService(
                inventoryClient,
                orderCreationValidator,
                orderCommandService
        );
    }

    @Test
    void createOrder_shouldPrepareDataAndDelegatePersistence() {
        CreateOrderRequest request = createValidRequest();
        OrderResponse orderResponse = createOrderResponse();
        mockInventoryProducts(request);

        when(orderCommandService.persistOrder(any(PreparedOrderData.class)))
                .thenReturn(orderResponse);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result).isSameAs(orderResponse);

        verify(orderCommandService).persistOrder(preparedOrderDataCaptor.capture());

        PreparedOrderData preparedOrder = preparedOrderDataCaptor.getValue();
        assertThat(preparedOrder.userId()).isEqualTo(request.userId());
        assertThat(preparedOrder.currency()).isEqualTo("RUB");
        assertThat(preparedOrder.items()).containsExactly(
                new PreparedOrderItem(
                        BICYCLE_PRODUCT_ID,
                        "Bicycle",
                        new BigDecimal("444.44"),
                        2
                )
        );
    }

    @Test
    void createOrder_withMultipleItems_shouldPrepareEveryItem() {
        CreateOrderRequest request = createValidRequestWithMultipleItems();
        OrderResponse orderResponse = createOrderResponse();
        mockInventoryProducts(request);

        when(orderCommandService.persistOrder(any(PreparedOrderData.class)))
                .thenReturn(orderResponse);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result).isSameAs(orderResponse);

        verify(orderCommandService).persistOrder(preparedOrderDataCaptor.capture());
        assertThat(preparedOrderDataCaptor.getValue().items())
                .extracting(PreparedOrderItem::productId)
                .containsExactly(KEYBOARD_PRODUCT_ID, MOUSE_PRODUCT_ID);
    }

    @Test
    void createOrder_withDuplicateProducts_shouldMergeQuantities() {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(
                        new CreateOrderItemRequest(KEYBOARD_PRODUCT_ID, 2),
                        new CreateOrderItemRequest(MOUSE_PRODUCT_ID, 1),
                        new CreateOrderItemRequest(KEYBOARD_PRODUCT_ID, 3)
                )
        );
        OrderResponse orderResponse = createOrderResponse();
        mockInventoryProducts(request);

        when(orderCommandService.persistOrder(any(PreparedOrderData.class)))
                .thenReturn(orderResponse);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result).isSameAs(orderResponse);

        verify(orderCommandService).persistOrder(preparedOrderDataCaptor.capture());
        assertThat(preparedOrderDataCaptor.getValue().items())
                .containsExactly(
                        new PreparedOrderItem(
                                KEYBOARD_PRODUCT_ID,
                                "Keyboard",
                                new BigDecimal("10.50"),
                                5
                        ),
                        new PreparedOrderItem(
                                MOUSE_PRODUCT_ID,
                                "Mouse",
                                new BigDecimal("25.00"),
                                1
                        )
                );
    }

    @Test
    void createOrder_withDifferentCurrencies_shouldRejectOrder() {
        CreateOrderRequest request = createValidRequestWithMultipleItems();
        Set<UUID> requestedIds = Set.of(KEYBOARD_PRODUCT_ID, MOUSE_PRODUCT_ID);

        when(inventoryClient.getProductsSnapshot(new ProductsBatchRequest(requestedIds)))
                .thenReturn(List.of(
                        new ProductSnapshot(
                                KEYBOARD_PRODUCT_ID,
                                "Keyboard",
                                new BigDecimal("10.50"),
                                "RUB",
                                ProductStatus.ACTIVE
                        ),
                        new ProductSnapshot(
                                MOUSE_PRODUCT_ID,
                                "Mouse",
                                new BigDecimal("25.00"),
                                "USD",
                                ProductStatus.ACTIVE
                        )
                ));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(MixedOrderCurrenciesException.class)
                .hasMessageContaining("RUB")
                .hasMessageContaining("USD");

        verifyNoInteractions(orderCommandService);
    }

    @Test
    void createOrder_whenInventoryResponseMissesProduct_shouldRejectOrder() {
        CreateOrderRequest request = createValidRequestWithMultipleItems();
        Set<UUID> requestedIds = Set.of(KEYBOARD_PRODUCT_ID, MOUSE_PRODUCT_ID);

        when(inventoryClient.getProductsSnapshot(new ProductsBatchRequest(requestedIds)))
                .thenReturn(List.of(
                        new ProductSnapshot(
                                KEYBOARD_PRODUCT_ID,
                                "Keyboard",
                                new BigDecimal("10.50"),
                                "RUB",
                                ProductStatus.ACTIVE
                        )
                ));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(MOUSE_PRODUCT_ID.toString());

        verifyNoInteractions(orderCommandService);
    }

    @Test
    void createOrder_shouldNotPersistWhenProductUnavailable() {
        CreateOrderRequest request = createValidRequest();
        ProductSnapshot unavailableProduct = new ProductSnapshot(
                BICYCLE_PRODUCT_ID,
                "Bicycle",
                new BigDecimal("444.44"),
                "RUB",
                ProductStatus.INACTIVE
        );

        when(inventoryClient.getProductsSnapshot(any()))
                .thenReturn(List.of(unavailableProduct));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ProductUnavailableException.class);

        verifyNoInteractions(orderCommandService);
    }


    @Test
    void persistOrder_shouldCreateOutboxEventWithMatchingEventIdAndContractMetadata() {
        Instant timeNow = NOW;
        UUID userId = UUID.randomUUID();

        List<OrderCreatedItem> itemPayloads = List.of(
                new OrderCreatedItem(
                        KEYBOARD_PRODUCT_ID,
                        "Keyboard",
                        new BigDecimal("10.50"),
                        "RUB",
                        2
                ),
                new OrderCreatedItem(
                        MOUSE_PRODUCT_ID,
                        "Mouse",
                        new BigDecimal("25.00"),
                        "RUB",
                        1
                )
        );

        PreparedOrderData preparedOrder = new PreparedOrderData(
                userId,
                "RUB",
                List.of(
                        new PreparedOrderItem(
                                KEYBOARD_PRODUCT_ID,
                                "Keyboard",
                                new BigDecimal("10.50"),
                                2
                        ),
                        new PreparedOrderItem(
                                MOUSE_PRODUCT_ID,
                                "Mouse",
                                new BigDecimal("25.00"),
                                1
                        )
                )
        );

        when(orderRepository.save(any(OrderEntity.class)))
                .thenAnswer(invocation -> {
                    OrderEntity order = invocation.getArgument(0);
                    ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
                    return order;
                });
        when(orderMapper.toItemsPayload(anyList()))
                .thenReturn(itemPayloads);
        when(objectMapper.writeValueAsString(any(OrderCreatedEvent.class)))
                .thenAnswer(invocation -> {
                    OrderCreatedEvent event = invocation.getArgument(0);
                    return "{\"orderId\":\"%s\"}".formatted(event.orderId());
                });

        actualOrderCommandService.persistOrder(preparedOrder);

        verify(orderRepository).save(orderCaptor.capture());
        verify(outboxEventRepository).save(outboxEventCaptor.capture());

        OrderEntity order = orderCaptor.getValue();
        OutboxEventEntity outboxEvent = outboxEventCaptor.getValue();

        assertThat(outboxEvent.getAggregateType()).isEqualTo("Order");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(order.getId());
        assertThat(outboxEvent.getEventType()).isEqualTo("OrderCreatedEvent");
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(outboxEvent.getPayload()).contains(order.getId().toString());
        assertThat(outboxEvent.getCreatedAt()).isAfterOrEqualTo(timeNow);

        verify(objectMapper).writeValueAsString(orderCreatedEventCaptor.capture());

        OrderCreatedEvent event = orderCreatedEventCaptor.getValue();

        assertThat(outboxEvent.getId()).isEqualTo(event.eventId());
        assertThat(outboxEvent.getEventType()).isEqualTo(event.eventType());
        assertThat(event.eventType()).isEqualTo(OrderCreatedEvent.TYPE);
        assertThat(event.eventVersion()).isEqualTo(OrderCreatedEvent.VERSION);
        assertThat(event.orderId()).isEqualTo(order.getId());
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.items()).isEqualTo(itemPayloads);
        assertThat(event.items()).allMatch(item -> item.currency().equals("RUB"));
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

        OrderResponse result = orderQueryService.getOrderById(orderId);

        assertThat(result).isSameAs(response);

        verify(orderRepository).findById(orderId);
        verify(orderMapper).toOrderResponse(order);
    }


    @Test
    void getOrderById_missingOrder_shouldThrowOrderNotFoundException() {
        UUID orderId = UUID.randomUUID();

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderQueryService.getOrderById(orderId))
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
                "RUB",
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

        OrderSummaryResponseList result = orderQueryService.getOrdersByUserId(userId);

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

        OrderSummaryResponseList result = orderQueryService.getOrdersByUserId(userId);

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

        List<OrderHistoryResponse> result = orderQueryService.getOrderHistoryById(orderId);

        assertThat(result).isSameAs(response);

        verify(orderRepository).existsById(orderId);
        verify(orderHistoryRepository).findOrderHistoryByIdByCreatedTimeAsc(orderId);
    }


    @Test
    void getOrderHistoryById_missingOrder_shouldThrowOrderNotFoundException() {
        UUID orderId = UUID.randomUUID();

        when(orderRepository.existsById(orderId)).thenReturn(false);

        assertThatThrownBy(() -> orderQueryService.getOrderHistoryById(orderId))
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
                "RUB",
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
    void markPaymentSucceeded_shouldChangeStatusAndSaveHistory() {
        OrderEntity order = orderWithStatus(OrderStatus.WAITING_FOR_PAYMENT);

        when(orderRepository.findById(order.getId()))
                .thenReturn(Optional.of(order));

        actualOrderCommandService.markPaymentSucceeded(order.getId());

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

        actualOrderCommandService.markPaymentFailed(order.getId());

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

        actualOrderCommandService.cancelOrder(order.getId());

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

        assertThatThrownBy(() -> actualOrderCommandService.markPaymentSucceeded(order.getId()))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orderRepository).findById(order.getId());
        verifyNoInteractions(orderHistoryRepository);
    }

    @Test
    void reserveInventory_shouldThrowOrderNotFoundException() {
        UUID orderId = UUID.randomUUID();

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actualOrderCommandService.reserveInventory(orderId))
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
                BICYCLE_PRODUCT_ID,
                2
        );

        return new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(item1)
        );
    }

    private CreateOrderRequest createValidRequestWithMultipleItems() {
        CreateOrderItemRequest item1 = new CreateOrderItemRequest(
                KEYBOARD_PRODUCT_ID,
                2
        );

        CreateOrderItemRequest item2 = new CreateOrderItemRequest(
                MOUSE_PRODUCT_ID,
                1
        );

        return new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(item1, item2)
        );
    }

    private void mockInventoryProducts(CreateOrderRequest request) {
        Map<UUID, ProductSnapshot> productsById = Map.of(
                BICYCLE_PRODUCT_ID,
                new ProductSnapshot(
                        BICYCLE_PRODUCT_ID,
                        "Bicycle",
                        new BigDecimal("444.44"),
                        "RUB",
                        ProductStatus.ACTIVE
                ),
                KEYBOARD_PRODUCT_ID,
                new ProductSnapshot(
                        KEYBOARD_PRODUCT_ID,
                        "Keyboard",
                        new BigDecimal("10.50"),
                        "RUB",
                        ProductStatus.ACTIVE
                ),
                MOUSE_PRODUCT_ID,
                new ProductSnapshot(
                        MOUSE_PRODUCT_ID,
                        "Mouse",
                        new BigDecimal("25.00"),
                        "RUB",
                        ProductStatus.ACTIVE
                )
        );

        Set<UUID> requestedIds = request.items().stream()
                .map(CreateOrderItemRequest::productId)
                .collect(Collectors.toSet());

        List<ProductSnapshot> products = requestedIds.stream()
                .map(productsById::get)
                .toList();

        when(inventoryClient.getProductsSnapshot(new ProductsBatchRequest(requestedIds)))
                .thenReturn(products);
    }
}
