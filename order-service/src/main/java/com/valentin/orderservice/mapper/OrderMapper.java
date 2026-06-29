package com.valentin.orderservice.mapper;


import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.OrderHistoryEntity;
import com.valentin.orderservice.domain.OrderItemEntity;
import com.valentin.orderservice.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderResponse toOrderResponse(OrderEntity entity);

    OrderItemResponse toOrderItemResponse(OrderItemEntity entity);

    @Mapping(target = "orderId", source = "order.id")
    OrderHistoryResponse toOrderHistoryResponse(OrderHistoryEntity entity);

    List<OrderHistoryResponse> toOrderHistoryResponseList(List<OrderHistoryEntity> entities);
}