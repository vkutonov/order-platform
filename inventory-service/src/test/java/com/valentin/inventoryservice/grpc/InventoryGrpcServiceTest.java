package com.valentin.inventoryservice.grpc;

import com.valentin.inventory.grpc.GetProductSnapshotsRequest;
import com.valentin.inventory.grpc.InventoryGrpcMetadata;
import com.valentin.inventory.grpc.InventoryQueryServiceGrpc;
import com.valentin.inventory.grpc.ProductState;
import com.valentin.inventoryservice.domain.dictionary.ProductStatus;
import com.valentin.inventoryservice.dto.ProductSnapshotResponse;
import com.valentin.inventoryservice.exception.ProductNotFoundException;
import com.valentin.inventoryservice.logic.ProductService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InventoryGrpcServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MISSING_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private ProductService productService;
    private Server server;
    private ManagedChannel channel;
    private InventoryQueryServiceGrpc.InventoryQueryServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        productService = mock(ProductService.class);
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName).directExecutor()
                .addService(new InventoryGrpcService(productService)).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        stub = InventoryQueryServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE, PRODUCT_STATE_ACTIVE", "INACTIVE, PRODUCT_STATE_INACTIVE", "DELETED, PRODUCT_STATE_DELETED"})
    void getProductSnapshots_preservesFieldsAndExactPrice(ProductStatus status, ProductState grpcStatus) {
        BigDecimal price = new BigDecimal("12345678901234567890.12345678901234567890");
        when(productService.findProducts(Set.of(PRODUCT_ID))).thenReturn(List.of(
                new ProductSnapshotResponse(PRODUCT_ID, "Keyboard", price, "RUB", status)
        ));

        var response = stub.getProductSnapshots(GetProductSnapshotsRequest.newBuilder()
                .addProductIds(PRODUCT_ID.toString()).build());

        assertThat(response.getProductsCount()).isEqualTo(1);
        var product = response.getProducts(0);
        assertThat(product.getProductId()).isEqualTo(PRODUCT_ID.toString());
        assertThat(product.getProductName()).isEqualTo("Keyboard");
        assertThat(product.getUnitPrice()).isEqualTo(price.toPlainString());
        assertThat(product.getCurrency()).isEqualTo("RUB");
        assertThat(product.getStatus()).isEqualTo(grpcStatus);
        verify(productService).findProducts(Set.of(PRODUCT_ID));
    }

    @Test
    void getProductSnapshots_whenSomeProductsAreMissing_returnsExactIdsInTrailers() {
        when(productService.findProducts(Set.of(PRODUCT_ID, MISSING_ID)))
                .thenThrow(new ProductNotFoundException(Set.of(MISSING_ID)));

        assertThatThrownBy(() -> stub.getProductSnapshots(GetProductSnapshotsRequest.newBuilder()
                .addProductIds(PRODUCT_ID.toString()).addProductIds(MISSING_ID.toString()).build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, exception -> {
                    assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                    assertThat(exception.getTrailers()).isNotNull();
                    assertThat(exception.getTrailers().get(InventoryGrpcMetadata.MISSING_PRODUCTS)
                            .getProductIdsList()).containsExactly(MISSING_ID.toString());
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-uuid"})
    void getProductSnapshots_whenRequestIsInvalid_returnsInvalidArgument(String id) {
        var request = GetProductSnapshotsRequest.newBuilder();
        if (!id.isEmpty()) {
            request.addProductIds(id);
        }

        assertThatThrownBy(() -> stub.getProductSnapshots(request.build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        exception -> assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        verifyNoInteractions(productService);
    }

    @Test
    void getProductSnapshots_whenDependencyFails_doesNotExposeInternalDetails() {
        when(productService.findProducts(Set.of(PRODUCT_ID)))
                .thenThrow(new IllegalStateException("Internal database details"));

        assertThatThrownBy(() -> stub.getProductSnapshots(GetProductSnapshotsRequest.newBuilder()
                .addProductIds(PRODUCT_ID.toString()).build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, exception -> {
                    assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                    assertThat(exception.getStatus().getDescription()).isEqualTo("Failed to get product snapshots");
                });
    }
}
