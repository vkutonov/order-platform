package com.valentin.orderservice.api;

import com.jayway.jsonpath.JsonPath;
import com.valentin.orderservice.client.InventoryClient;
import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.OrderHistoryRepository;
import com.valentin.orderservice.domain.dictionary.ProductStatus;
import com.valentin.orderservice.dto.ProductSnapshot;
import com.valentin.orderservice.dto.ProductsBatchRequest;
import com.valentin.orderservice.messaging.outbox.OutboxEventPoller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers
class OrderControllerIntegrationTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FIRST_PRODUCT_ID = UUID.randomUUID();
    private static final UUID SECOND_PRODUCT_ID = UUID.randomUUID();
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18.4-bookworm");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("order_test_db")
            .withUsername("order_test_user")
            .withPassword("order_test_password");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderHistoryRepository orderHistoryRepository;

    @MockitoBean
    InventoryClient inventoryClient;

    @MockitoBean
    OutboxEventPoller poller;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        orderHistoryRepository.deleteAll();
        orderRepository.deleteAll();

        when(inventoryClient.getProductsSnapshot(any(ProductsBatchRequest.class)))
                .thenReturn(List.of(
                        new ProductSnapshot(
                                FIRST_PRODUCT_ID,
                                "Keyboard",
                                new BigDecimal("10.50"),
                                "RUB",
                                ProductStatus.ACTIVE
                        ),
                        new ProductSnapshot(
                                SECOND_PRODUCT_ID,
                                "Mouse",
                                new BigDecimal("25.00"),
                                "RUB",
                                ProductStatus.ACTIVE
                        )
                ));
    }

    @Test
    void createOrder_returnsCreatedOrderAndPersistsHistory() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateOrderJson()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_INVENTORY"))
                .andExpect(jsonPath("$.totalPrice").value(46.0))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.orderItems", hasSize(2)))
                .andExpect(jsonPath("$.orderItems[0].productId").value(FIRST_PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.orderItems[0].productName").value("Keyboard"))
                .andExpect(jsonPath("$.orderItems[0].unitPrice").value(10.5))
                .andExpect(jsonPath("$.orderItems[0].currency").value("RUB"))
                .andExpect(jsonPath("$.orderItems[0].quantity").value(2))
                .andExpect(jsonPath("$.orderItems[0].totalPrice").value(21.0))
                .andExpect(jsonPath("$.orderItems[1].productId").value(SECOND_PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.orderItems[1].productName").value("Mouse"))
                .andExpect(jsonPath("$.orderItems[1].unitPrice").value(25.0))
                .andExpect(jsonPath("$.orderItems[1].currency").value("RUB"))
                .andExpect(jsonPath("$.orderItems[1].quantity").value(1))
                .andExpect(jsonPath("$.orderItems[1].totalPrice").value(25.0));

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderHistoryRepository.count()).isEqualTo(1);
    }

    @Test
    void createOrder_withDifferentCurrencies_returnsUnprocessableContent() throws Exception {
        when(inventoryClient.getProductsSnapshot(any(ProductsBatchRequest.class)))
                .thenReturn(List.of(
                        new ProductSnapshot(
                                FIRST_PRODUCT_ID,
                                "Keyboard",
                                new BigDecimal("10.50"),
                                "RUB",
                                ProductStatus.ACTIVE
                        ),
                        new ProductSnapshot(
                                SECOND_PRODUCT_ID,
                                "Mouse",
                                new BigDecimal("25.00"),
                                "USD",
                                ProductStatus.ACTIVE
                        )
                ));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateOrderJson()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("MIXED_ORDER_CURRENCIES"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("RUB")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("USD")));

        assertThat(orderRepository.count()).isZero();
        assertThat(orderHistoryRepository.count()).isZero();
    }

    @Test
    void getOrderById_whenOrderExists_returnsOrder() throws Exception {
        String createResponse = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateOrderJson()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderId = JsonPath.read(createResponse, "$.id");

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_INVENTORY"))
                .andExpect(jsonPath("$.totalPrice").value(46.0))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.orderItems", hasSize(2)))
                .andExpect(jsonPath("$.orderItems[0].currency").value("RUB"))
                .andExpect(jsonPath("$.orderItems[1].currency").value("RUB"));
    }

    @Test
    void getOrderById_whenOrderDoesNotExist_returnsNotFound() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order not found id = " + orderId))
                .andExpect(jsonPath("$.path").value("/api/orders/" + orderId));
    }

    @Test
    void getOrdersByUserId_whenUserHasOrders_returnsOnlyThatUserOrders() throws Exception {
        UUID userId = UUID.randomUUID();

        String firstOrderId = createOrderForUser(USER_ID);
        String secondOrderId = createOrderForUser(USER_ID);
        String userOrderId = createOrderForUser(userId);

        String response = mockMvc.perform(get("/api/orders/user/{userId}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalPrice").value(92.0))
                .andExpect(jsonPath("$.orderSummaryResponses", hasSize(2)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> orderIds = JsonPath.read(response, "$.orderSummaryResponses[*].id");
        List<String> userIds = JsonPath.read(response, "$.orderSummaryResponses[*].userId");

        assertThat(orderIds).containsExactlyInAnyOrder(firstOrderId, secondOrderId);
        assertThat(orderIds).doesNotContain(userOrderId);
        assertThat(userIds).containsOnly(USER_ID.toString());
    }

    @Test
    void getOrdersByUserId_whenUserHasNoOrders_returnsEmptyList() throws Exception {
        UUID randomUserId = UUID.randomUUID();

        mockMvc.perform(get("/api/orders/user/{userId}", randomUserId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalPrice").value(0))
                .andExpect(jsonPath("$.orderSummaryResponses").isArray())
                .andExpect(jsonPath("$.orderSummaryResponses", hasSize(0)));
    }


    @Test
    void createOrder_whenRequestIsInvalid_returnsValidationError() throws Exception {
        String invalidRequest = """
                {
                  "userId": null,
                  "items": []
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/orders"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)));
    }

    private String createOrderForUser(UUID userId) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateOrderJson(userId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private static String validCreateOrderJson() {
        return validCreateOrderJson(USER_ID);
    }

    private static String validCreateOrderJson(UUID userId) {
        return """
                {
                  "userId": "%s",
                  "items": [
                    {
                      "productId": "%s",
                      "quantity": 2
                    },
                    {
                      "productId": "%s",
                      "quantity": 1
                    }
                  ]
                }
                """.formatted(userId, FIRST_PRODUCT_ID, SECOND_PRODUCT_ID);
    }
}
