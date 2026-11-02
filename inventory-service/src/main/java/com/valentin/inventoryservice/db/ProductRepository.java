package com.valentin.inventoryservice.db;

import com.valentin.inventoryservice.domain.ProductEntity;
import com.valentin.inventoryservice.domain.dictionary.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    List<ProductEntity> findAllByStatusNot(ProductStatus status);
}
