package com.valentin.orderservice.api;

import com.valentin.inventory.grpc.GetProductSnapshotsRequest;
import com.valentin.inventory.grpc.GetProductSnapshotsResponse;
import com.valentin.inventory.grpc.InventoryGrpcMetadata;
import com.valentin.inventory.grpc.InventoryQueryServiceGrpc;
import com.valentin.inventory.grpc.MissingProducts;
import com.valentin.orderservice.client.GrpcInventoryClient;
import com.valentin.orderservice.exception.GlobalExceptionHandler;
import com.valentin.orderservice.logic.OrderCommandService;
import com.valentin.orderservice.logic.OrderCreationValidator;
import com.valentin.orderservice.logic.OrderQueryService;
import com.valentin.orderservice.logic.OrderService;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerInventoryErrorTest {

    private static final String PRODUCT_ID = "32e53b82-70e4-4ed5-876d-e6f99aab2691";
    private static final String ORDER_REQUEST = """
            {
              "userId": "00000000-0000-0000-0000-000000000001",
              "items": [
                {"productId": "%s", "quantity": 1},
                {"productId": "00000000-0000-0000-0000-000000000002", "quantity": 1}
              ]
            }
            """.formatted(PRODUCT_ID);

    private Server server;
    private ManagedChannel channel;
    private Consumer<StreamObserver<GetProductSnapshotsResponse>> inventoryResponse;
    private GetProductSnapshotsRequest inventoryRequest;
    private MockMvc mockMvc;
    private OrderCommandService commandService;

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
        var inventoryClient = new GrpcInventoryClient(InventoryQueryServiceGrpc.newBlockingStub(channel));

        commandService = mock(OrderCommandService.class);
        OrderService orderService = new OrderService(
                inventoryClient,
                new OrderCreationValidator(),
                commandService
        );
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                Clock.fixed(Instant.parse("2026-10-08T04:08:33Z"), ZoneOffset.UTC)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(
                new OrderController(orderService, commandService, mock(OrderQueryService.class))
        ).setControllerAdvice(handler).build();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void createOrder_whenInventoryProductNotFound_returnsOnlyMissingIds() throws Exception {
        Metadata trailers = new Metadata();
        trailers.put(InventoryGrpcMetadata.MISSING_PRODUCTS,
                MissingProducts.newBuilder().addProductIds(PRODUCT_ID).build());
        inventoryResponse = observer -> observer.onError(Status.NOT_FOUND
                .withDescription("Internal inventory details").asRuntimeException(trailers));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order creation failed, products missed"))
                .andExpect(jsonPath("$.missingProductIds.length()").value(1))
                .andExpect(jsonPath("$.missingProductIds[0]").value(PRODUCT_ID))
                .andExpect(jsonPath("$.path").value("/api/orders"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-08T04:08:33Z"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        assertThat(inventoryRequest.getProductIdsList()).hasSize(2).contains(PRODUCT_ID);
        verifyNoInteractions(commandService);
    }

    @Test
    void createOrder_whenNotFoundHasNoTrailers_doesNotGuessMissingIds() throws Exception {
        inventoryResponse = observer -> observer.onError(Status.NOT_FOUND
                .withDescription("Products not found: productIds=[" + PRODUCT_ID + "]")
                .asRuntimeException());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.missingProductIds").doesNotExist());

        verifyNoInteractions(commandService);
    }

    @Test
    void createOrder_whenRequestIsInvalid_preservesFieldErrors() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":null,\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));

        assertThat(inventoryRequest).isNull();
        verifyNoInteractions(commandService);
    }

    @ParameterizedTest
    @CsvSource({
            "UNAVAILABLE, 503, INVENTORY_UNAVAILABLE, Inventory is unavailable",
            "DEADLINE_EXCEEDED, 504, INVENTORY_TIMEOUT, Inventory request timed out"
    })
    void createOrder_whenInventoryIsUnavailableOrTimesOut_returnsGatewayError(
            Status.Code grpcCode, int httpStatus, String code, String message
    ) throws Exception {
        assertInventoryFailure(grpcCode, httpStatus, code, message);
    }

    @ParameterizedTest
    @EnumSource(value = Status.Code.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"OK", "NOT_FOUND", "UNAVAILABLE", "DEADLINE_EXCEEDED"})
    void createOrder_whenInventoryFails_returnsBadGateway(Status.Code grpcCode) throws Exception {
        assertInventoryFailure(grpcCode, 502, "INVENTORY_ERROR", "Inventory request failed");
    }

    private void assertInventoryFailure(
            Status.Code grpcCode, int httpStatus, String code, String message
    ) throws Exception {
        inventoryResponse = observer -> observer.onError(Status.fromCode(grpcCode)
                .withDescription("Internal inventory details").asRuntimeException());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_REQUEST))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.status").value(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value("/api/orders"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-08T04:08:33Z"))
                .andExpect(jsonPath("$.missingProductIds").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(content().string(not(containsString("Internal inventory details"))));

        verifyNoInteractions(commandService);
    }
}
