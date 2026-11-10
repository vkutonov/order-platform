package com.valentin.orderservice.client;

import com.valentin.inventory.grpc.GetProductSnapshotsRequest;
import com.valentin.inventory.grpc.InventoryGrpcMetadata;
import com.valentin.inventory.grpc.InventoryQueryServiceGrpc;
import com.valentin.inventory.grpc.ProductSnapshotMessage;
import com.valentin.orderservice.domain.dictionary.ProductStatus;
import com.valentin.orderservice.dto.ProductSnapshot;
import com.valentin.orderservice.dto.ProductsBatchRequest;
import com.valentin.orderservice.exception.ApiErrorCode;
import com.valentin.orderservice.exception.InventoryClientException;
import com.valentin.orderservice.exception.ProductNotFoundException;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class GrpcInventoryClient implements InventoryClient {

    private final InventoryQueryServiceGrpc.InventoryQueryServiceBlockingStub stub;

    @Override
    public List<ProductSnapshot> getProductsSnapshot(ProductsBatchRequest request) {

        var grpcRequest = GetProductSnapshotsRequest.newBuilder()
                .addAllProductIds(
                        request.productIds().stream()
                                .map(UUID::toString)
                                .toList()
                )
                .build();

        try {
            var response = stub
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getProductSnapshots(grpcRequest);

            return response.getProductsList().stream()
                    .map(this::toSnapshot)
                    .toList();
        } catch (StatusRuntimeException exception) {
            throw switch (exception.getStatus().getCode()) {
                case NOT_FOUND -> toProductNotFoundException(exception);
                case UNAVAILABLE -> new InventoryClientException(
                        ApiErrorCode.INVENTORY_UNAVAILABLE, "Inventory is unavailable", exception
                );
                case DEADLINE_EXCEEDED -> new InventoryClientException(
                        ApiErrorCode.INVENTORY_TIMEOUT, "Inventory request timed out", exception
                );
                default -> new InventoryClientException(
                        ApiErrorCode.INVENTORY_ERROR, "Inventory request failed", exception
                );
            };
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new InventoryClientException(
                    ApiErrorCode.INVENTORY_ERROR, "Invalid response from inventory", exception
            );
        }
    }

    private ProductNotFoundException toProductNotFoundException(StatusRuntimeException exception) {
        try {
            var trailers = exception.getTrailers();
            var missingProducts = trailers != null
                    ? trailers.get(InventoryGrpcMetadata.MISSING_PRODUCTS)
                    : null;
            return new ProductNotFoundException(missingProducts != null
                    ? missingProducts.getProductIdsList().stream().map(UUID::fromString).toList()
                    : List.of());
        } catch (RuntimeException invalidDetails) {
            throw new InventoryClientException(
                    ApiErrorCode.INVENTORY_ERROR, "Invalid response from inventory", invalidDetails
            );
        }
    }

    private ProductSnapshot toSnapshot(ProductSnapshotMessage product) {
        ProductStatus status = switch (product.getStatus()) {
            case PRODUCT_STATE_ACTIVE -> ProductStatus.ACTIVE;
            case PRODUCT_STATE_INACTIVE -> ProductStatus.INACTIVE;
            case PRODUCT_STATE_DELETED -> ProductStatus.DELETED;
            case PRODUCT_STATE_UNSPECIFIED, UNRECOGNIZED -> throw new IllegalStateException(
                    "Inventory returned an unsupported product status"
            );
        };

        return new ProductSnapshot(
                UUID.fromString(product.getProductId()),
                product.getProductName(),
                new BigDecimal(product.getUnitPrice()),
                product.getCurrency(),
                status
        );
    }
}
