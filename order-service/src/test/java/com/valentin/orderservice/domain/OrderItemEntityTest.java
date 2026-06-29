package com.valentin.orderservice.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class OrderItemEntityTest {

    @Test
    public void create_shouldPopulateAllFieldsAndCalculateTotalPrice() {

        UUID productId = UUID.randomUUID();

        OrderItemEntity item = OrderItemEntity.create(
                productId,
                "Ice cream",
                new BigDecimal("67.00"),
                3
        );

        assertThat(item.getProductId()).isEqualTo(productId);
        assertThat(item.getProductName()).isEqualTo("Ice cream");
        assertThat(item.getUnitPrice()).isEqualByComparingTo(new BigDecimal("67.00"));
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getTotalPrice()).isEqualByComparingTo(new BigDecimal("201.00"));
    }
}
