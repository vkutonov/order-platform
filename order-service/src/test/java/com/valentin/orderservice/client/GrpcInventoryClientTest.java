package com.valentin.orderservice.client;

import com.valentin.inventory.grpc.GetProductSnapshotsRequest;
import com.valentin.inventory.grpc.GetProductSnapshotsResponse;
import com.valentin.inventory.grpc.InventoryGrpcMetadata;
import com.valentin.inventory.grpc.InventoryQueryServiceGrpc;
import com.valentin.inventory.grpc.MissingProducts;
import com.valentin.inventory.grpc.ProductSnapshotMessage;
import com.valentin.inventory.grpc.ProductState;
import com.valentin.orderservice.domain.dictionary.ProductStatus;
import com.valentin.orderservice.dto.ProductsBatchRequest;
import com.valentin.orderservice.exception.ApiErrorCode;
import com.valentin.orderservice.exception.InventoryClientException;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcInventoryClientTest {

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ProductsBatchRequest REQUEST = new ProductsBatchRequest(Set.of(PRODUCT_ID));

    private Server server;
    private ManagedChannel channel;
    private GrpcInventoryClient client;
    private Consumer<StreamObserver<GetProductSnapshotsResponse>> inventoryResponse;
    private GetProductSnapshotsRequest inventoryRequest;

    @BeforeEach
    void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName).directExecutor()
                .addService(new InventoryQueryServiceGrpc.InventoryQueryServiceImplBase() {
                    @Override
                    public void getProductSnapshots(
                            GetProductSnapshotsRequest request,
                            StreamObserver<GetProductSnapshotsResponse> observer
                    ) {
                        inventoryRequest = request;
                        inventoryResponse.accept(observer);
                    }
                }).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        client = new GrpcInventoryClient(InventoryQueryServiceGrpc.newBlockingStub(channel));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @CsvSource({"PRODUCT_STATE_ACTIVE, ACTIVE", "PRODUCT_STATE_INACTIVE, INACTIVE", "PRODUCT_STATE_DELETED, DELETED"})
    void getProductsSnapshot_preservesFieldsAndExactPrice(ProductState grpcStatus, ProductStatus status) {
        String price = "12345678901234567890.12345678901234567890";
        inventoryResponse = observer -> {
            observer.onNext(GetProductSnapshotsResponse.newBuilder().addProducts(
                    product().setUnitPrice(price).setStatus(grpcStatus)
            ).build());
            observer.onCompleted();
        };

        var products = client.getProductsSnapshot(REQUEST);

        assertThat(inventoryRequest.getProductIdsList()).containsExactly(PRODUCT_ID.toString());
        assertThat(products).hasSize(1);
        var product = products.getFirst();
        assertThat(product.productId()).isEqualTo(PRODUCT_ID);
        assertThat(product.productName()).isEqualTo("Keyboard");
        assertThat(product.unitPrice()).isEqualTo(new BigDecimal(price));
        assertThat(product.currency()).isEqualTo("RUB");
        assertThat(product.status()).isEqualTo(status);
    }

    @Test
    void getProductsSnapshot_whenServerIsUnavailable_returnsDependencyError() throws Exception {
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);

        assertThatThrownBy(() -> client.getProductsSnapshot(REQUEST))
                .isInstanceOfSatisfying(InventoryClientException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.INVENTORY_UNAVAILABLE));
    }

    @Test
    void getProductsSnapshot_whenServerDoesNotRespond_enforcesDeadline() {
        inventoryResponse = observer -> { };

        assertThatThrownBy(() -> client.getProductsSnapshot(REQUEST))
                .isInstanceOfSatisfying(InventoryClientException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.INVENTORY_TIMEOUT));
    }

    @Test
    void getProductsSnapshot_whenMissingIdsAreMalformed_returnsBadGateway() {
        Metadata trailers = new Metadata();
        trailers.put(InventoryGrpcMetadata.MISSING_PRODUCTS,
                MissingProducts.newBuilder().addProductIds("not-a-uuid").build());
        inventoryResponse = observer -> observer.onError(Status.NOT_FOUND.asRuntimeException(trailers));

        assertBadGateway();
    }

    @ParameterizedTest
    @ValueSource(strings = {"product-id", "price", "status", "unknown-status"})
    void getProductsSnapshot_whenSnapshotIsMalformed_returnsBadGateway(String invalidField) {
        var product = product();
        switch (invalidField) {
            case "product-id" -> product.setProductId("not-a-uuid");
            case "price" -> product.setUnitPrice("not-a-price");
            case "status" -> product.setStatus(ProductState.PRODUCT_STATE_UNSPECIFIED);
            case "unknown-status" -> product.setStatusValue(99);
        }
        inventoryResponse = observer -> {
            observer.onNext(GetProductSnapshotsResponse.newBuilder().addProducts(product).build());
            observer.onCompleted();
        };

        assertBadGateway();
    }

    private void assertBadGateway() {
        assertThatThrownBy(() -> client.getProductsSnapshot(REQUEST))
                .isInstanceOfSatisfying(InventoryClientException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.INVENTORY_ERROR));
    }

    private ProductSnapshotMessage.Builder product() {
        return ProductSnapshotMessage.newBuilder()
                .setProductId(PRODUCT_ID.toString())
                .setProductName("Keyboard")
                .setUnitPrice("10.50")
                .setCurrency("RUB")
                .setStatus(ProductState.PRODUCT_STATE_ACTIVE);
    }
}
