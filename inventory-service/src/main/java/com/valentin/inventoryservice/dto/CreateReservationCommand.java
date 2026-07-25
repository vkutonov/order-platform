package com.valentin.inventoryservice.dto;


import com.valentin.inventoryservice.exception.ApiErrorCode;
import com.valentin.inventoryservice.exception.DuplicateReservationProductException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CreateReservationCommand(
        @NotNull
        UUID orderId,
        @NotNull
        UUID userId,
        @NotEmpty
        List<@Valid ReservationItemCommand> items
) {
    public CreateReservationCommand {
        items = List.copyOf(items);

        Set<UUID> productIds = new HashSet<>();

        for (ReservationItemCommand item : items) {
            if (!productIds.add(item.productId())) {
                throw new DuplicateReservationProductException(
                        ApiErrorCode.DUPLICATE_RESERVATION_PRODUCT,
                        item.productId()
                );
            }
        }
    }
}
