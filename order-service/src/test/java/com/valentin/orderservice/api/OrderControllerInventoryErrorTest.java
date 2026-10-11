package com.valentin.orderservice.api;

import com.valentin.orderservice.client.InventoryClient;
import com.valentin.orderservice.exception.GlobalExceptionHandler;
import com.valentin.orderservice.logic.OrderCommandService;
import com.valentin.orderservice.logic.OrderCreationValidator;
import com.valentin.orderservice.logic.OrderQueryService;
import com.valentin.orderservice.logic.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private MockRestServiceServer inventoryServer;
    private MockMvc mockMvc;
    private OrderCommandService commandService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://inventory-service");
        inventoryServer = MockRestServiceServer.bindTo(builder).build();
        InventoryClient inventoryClient = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(builder.build()))
                .build()
                .createClient(InventoryClient.class);

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

    @Test
    void createOrder_whenInventoryProductNotFound_returnsOnlyMissingIds() throws Exception {
        respondFromInventory(HttpStatus.NOT_FOUND, """
                {
                  "status": 404,
                  "code": "PRODUCT_NOT_FOUND",
                  "message": "Некоторые товары не найдены",
                  "missingProductIds": ["%s"],
                  "path": "/internal/products/batch"
                }
                """.formatted(PRODUCT_ID));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("Не удалось создать заказ: некоторые товары отсутствуют"))
                .andExpect(jsonPath("$.missingProductIds.length()").value(1))
                .andExpect(jsonPath("$.missingProductIds[0]").value(PRODUCT_ID))
                .andExpect(jsonPath("$.path").value("/api/orders"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        inventoryServer.verify();
        verifyNoInteractions(commandService);
    }

    @Test
    void createOrder_whenOldInventoryResponseHasNoIds_returnsClean404() throws Exception {
        respondFromInventory(HttpStatus.NOT_FOUND, """
                {"status":404,"error":"Not Found","code":"PRODUCT_NOT_FOUND",
                 "message":"Products not found: productIds=[%s]"}
                """.formatted(PRODUCT_ID));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("Не удалось создать заказ: некоторые товары отсутствуют"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.missingProductIds").doesNotExist());

        inventoryServer.verify();
        verifyNoInteractions(commandService);
    }

    @Test
    void createOrder_whenRequestIsInvalid_preservesFieldErrors() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":null,\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.missingProductIds").doesNotExist());

        inventoryServer.verify();
        verifyNoInteractions(commandService);
    }

    @ParameterizedTest
    @MethodSource("unmappedInventoryErrors")
    void createOrder_whenInventoryFailureIsNotProductNotFound_returns500(
            HttpStatus upstreamStatus,
            String payload
    ) throws Exception {
        respondFromInventory(upstreamStatus, payload);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_REQUEST))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.error").doesNotExist());

        inventoryServer.verify();
        verifyNoInteractions(commandService);
    }

    private static Stream<Arguments> unmappedInventoryErrors() {
        return Stream.of(
                Arguments.of(HttpStatus.NOT_FOUND, "{\"code\":\"RESOURCE_NOT_FOUND\"}"),
                Arguments.of(HttpStatus.NOT_FOUND, "{\"status\":404}"),
                Arguments.of(HttpStatus.NOT_FOUND, "not-json"),
                Arguments.of(HttpStatus.NOT_FOUND, "null"),
                Arguments.of(HttpStatus.NOT_FOUND, """
                        {"code":"PRODUCT_NOT_FOUND","missingProductIds":["invalid-uuid"]}
                        """),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, "{\"code\":\"PRODUCT_NOT_FOUND\"}")
        );
    }

    private void respondFromInventory(HttpStatus upstreamStatus, String payload) {
        inventoryServer.expect(requestTo("http://inventory-service/internal/products/batch"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(upstreamStatus)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload));
    }
}
