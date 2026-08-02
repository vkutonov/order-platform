package com.valentin.orderservice.exception;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public final class ProductUnavailableException extends OrderServiceException {

    private final Set<UUID> productIds;

    public ProductUnavailableException(Collection<UUID> productIds) {
        super(
                ApiErrorCode.PRODUCT_UNAVAILABLE,
                "Products are unavailable: productIds=%s".formatted(productIds)
        );
        this.productIds = Set.copyOf(productIds);
    }

    public Set<UUID> getProductIds() {
        return productIds;
    }
}
