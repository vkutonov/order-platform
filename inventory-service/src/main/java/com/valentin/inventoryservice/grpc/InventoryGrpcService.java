package com.valentin.inventoryservice.grpc;

import com.valentin.inventory.grpc.GetProductSnapshotsRequest;
import com.valentin.inventory.grpc.GetProductSnapshotsResponse;
import com.valentin.inventory.grpc.InventoryQueryServiceGrpc;
import com.valentin.inventory.grpc.ProductSnapshotMessage;
import com.valentin.inventory.grpc.ProductState;
import com.valentin.inventoryservice.dto.ProductSnapshotResponse;
import com.valentin.inventoryservice.exception.ProductNotFoundException;
import com.valentin.inventoryservice.logic.ProductService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryGrpcService
        extends InventoryQueryServiceGrpc.InventoryQueryServiceImplBase {

    private final ProductService productService;

    @Override
    public void getProductSnapshots(
            GetProductSnapshotsRequest request,
            StreamObserver<GetProductSnapshotsResponse> responseObserver
    ) {
        if (request.getProductIdsCount() == 0) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Product IDs must not be empty")
                            .asRuntimeException()
            );
            return;
        }

        Set<UUID> productIds;

        try {
            productIds = request.getProductIdsList().stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Invalid product ID")
                            .asRuntimeException()
            );
            return;
        }

        try {
            var products = productService.findProducts(productIds);

            var response = GetProductSnapshotsResponse.newBuilder()
                    .addAllProducts(
                            products.stream()
                                    .map(this::toMessage)
                                    .toList()
                    )
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (ProductNotFoundException exception) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        } catch (Exception exception) {
            log.error("Failed to get product snapshots", exception);

            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Failed to get product snapshots")
                            .asRuntimeException()
            );
        }
    }

    private ProductSnapshotMessage toMessage(
            ProductSnapshotResponse product
    ) {
        ProductState status = switch (product.status()) {
            case ACTIVE -> ProductState.PRODUCT_STATE_ACTIVE;
            case INACTIVE -> ProductState.PRODUCT_STATE_INACTIVE;
            case DELETED -> ProductState.PRODUCT_STATE_DELETED;
        };

        return ProductSnapshotMessage.newBuilder()
                .setProductId(product.productId().toString())
                .setProductName(product.productName())
                .setUnitPrice(product.unitPrice().toPlainString())
                .setCurrency(product.currency())
                .setStatus(status)
                .build();
    }
}