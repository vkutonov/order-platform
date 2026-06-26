package com.valentin.orderservice.mapper;


import com.valentin.orderservice.domain.OrderEntity;
import com.valentin.orderservice.domain.OrderItemEntity;
import com.valentin.orderservice.dto.CreateOrderItemRequest;
import com.valentin.orderservice.dto.CreateOrderRequest;
import com.valentin.orderservice.dto.OrderItemResponse;
import com.valentin.orderservice.dto.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    public OrderResponse toOrderResponse(OrderEntity entity);

    public OrderItemResponse toOrderItemResponse(OrderItemEntity entity);
}