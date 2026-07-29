package com.valentin.inventoryservice.mapper;

import com.valentin.inventoryservice.domain.ProductEntity;
import com.valentin.inventoryservice.dto.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants.ComponentModel;

@Mapper(componentModel = ComponentModel.SPRING)
public interface ProductMapper {
    ProductResponse toProductResponse(ProductEntity productEntity);
}