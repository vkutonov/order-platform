package com.valentin.orderservice.exception;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public final class ProductNotFoundException extends OrderServiceException {

    private final Set<UUID> productIds;

    public ProductNotFoundException(Collection<UUID> productIds) {
        super(
                ApiErrorCode.PRODUCT_NOT_FOUND,
                "Products not found: productIds=%s".formatted(productIds)
        );
        this.productIds = Set.copyOf(productIds);
    }

    public Set<UUID> getProductIds() {
        return productIds;
    }
}
