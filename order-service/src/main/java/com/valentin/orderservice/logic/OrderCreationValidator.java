package com.valentin.orderservice.logic;

import com.valentin.orderservice.domain.dictionary.ProductStatus;
import com.valentin.orderservice.dto.*;
import com.valentin.orderservice.exception.MixedOrderCurrenciesException;
import com.valentin.orderservice.exception.ProductNotFoundException;
import com.valentin.orderservice.exception.ProductUnavailableException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OrderCreationValidator {

    public PreparedOrderData validateAndPrepare(
            CreateOrderRequest request,
            List<ProductSnapshot> products
    ) {
        Map<UUID, ProductSnapshot> productsById =
                products.stream()
                        .collect(Collectors.toMap(
                                ProductSnapshot::productId,
                                Function.identity()
                        ));

        Set<UUID> missingProductIds =
                request.items()
                        .stream()
                        .map(CreateOrderItemRequest::productId)
                        .collect(Collectors.toSet());

        missingProductIds.removeAll(
                productsById.keySet()
        );

        if (!missingProductIds.isEmpty()) {
            throw new ProductNotFoundException(
                    missingProductIds
            );
        }

        Set<UUID> unavailableProductIds =
                products.stream()
                        .filter(product ->
                                product.status()
                                        != ProductStatus.ACTIVE
                        )
                        .map(ProductSnapshot::productId)
                        .collect(Collectors.toSet());

        if (!unavailableProductIds.isEmpty()) {
            throw new ProductUnavailableException(
                    unavailableProductIds
            );
        }

        Set<String> currencies =
                products.stream()
                        .map(ProductSnapshot::currency)
                        .collect(Collectors.toSet());

        if (currencies.size() != 1) {
            throw new MixedOrderCurrenciesException(
                    currencies
            );
        }

        String currency =
                currencies.iterator().next();

        Map<UUID, Integer> quantitiesByProductId =
                request.items()
                        .stream()
                        .collect(Collectors.toMap(
                                CreateOrderItemRequest::productId,
                                CreateOrderItemRequest::quantity,
                                Integer::sum,
                                LinkedHashMap::new
                        ));

        List<PreparedOrderItem> preparedItems =
                quantitiesByProductId.entrySet()
                        .stream()
                        .map(entry -> {
                            ProductSnapshot product =
                                    productsById.get(
                                            entry.getKey()
                                    );

                            return new PreparedOrderItem(
                                    product.productId(),
                                    product.productName(),
                                    product.unitPrice(),
                                    entry.getValue()
                            );
                        })
                        .toList();

        return new PreparedOrderData(
                request.userId(),
                currency,
                preparedItems
        );
    }
}
