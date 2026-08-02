package com.valentin.orderservice.logic;

import com.valentin.orderservice.client.InventoryClient;
import com.valentin.orderservice.domain.*;
import com.valentin.orderservice.dto.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class OrderService {

    private final InventoryClient inventoryClient;

    private final OrderCreationValidator orderCreationValidator;

    private final OrderCommandService orderCommandService;


    public OrderResponse createOrder(CreateOrderRequest orderRequest) {

        Set<UUID> productIds = orderRequest.items().stream()
                .map(CreateOrderItemRequest::productId)
                .collect(Collectors.toUnmodifiableSet());

        List<ProductSnapshot> productSnapshots =
                inventoryClient.getProductsSnapshot(new ProductsBatchRequest(productIds));

        PreparedOrderData preparedOrderData =
                orderCreationValidator.validateAndPrepare(
                        orderRequest,
                        productSnapshots
        );

        return orderCommandService.persistOrder(preparedOrderData);
    }




}
