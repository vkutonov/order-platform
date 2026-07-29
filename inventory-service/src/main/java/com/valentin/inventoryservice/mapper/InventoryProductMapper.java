package com.valentin.inventoryservice.mapper;

import com.valentin.inventoryservice.domain.InventoryItemEntity;
import com.valentin.inventoryservice.domain.ProductEntity;
import com.valentin.inventoryservice.dto.InventoryProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;


@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InventoryProductMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "availableQuantity", source = "item.availableQuantity")
    InventoryProductResponse toResponse(ProductEntity product, InventoryItemEntity item);
}
