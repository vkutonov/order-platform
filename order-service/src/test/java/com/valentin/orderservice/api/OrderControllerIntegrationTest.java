package com.valentin.orderservice.api;

import com.jayway.jsonpath.JsonPath;
import com.valentin.orderservice.db.OrderRepository;
import com.valentin.orderservice.db.OrderStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderControllerIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRST_PRODUCT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SECOND_PRODUCT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
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
    OrderStatusHistoryRepository orderStatusHistoryRepository;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        orderStatusHistoryRepository.deleteAll();
        orderRepository.deleteAll();
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
                .andExpect(jsonPath("$.orderItems[0].quantity").value(2))
                .andExpect(jsonPath("$.orderItems[0].totalPrice").value(21.0))
                .andExpect(jsonPath("$.orderItems[1].productId").value(SECOND_PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.orderItems[1].productName").value("Mouse"))
                .andExpect(jsonPath("$.orderItems[1].unitPrice").value(25.0))
                .andExpect(jsonPath("$.orderItems[1].quantity").value(1))
                .andExpect(jsonPath("$.orderItems[1].totalPrice").value(25.0));

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderStatusHistoryRepository.count()).isEqualTo(1);
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
                .andExpect(jsonPath("$.orderItems", hasSize(2)));
    }

    @Test
    void getOrderById_whenOrderDoesNotExist_returnsNotFound() throws Exception {
        String missingOrderId = "99999999-9999-9999-9999-999999999999";

        mockMvc.perform(get("/api/orders/{id}", missingOrderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order not found id = " + missingOrderId))
                .andExpect(jsonPath("$.path").value("/api/orders/" + missingOrderId));
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
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/orders"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)));
    }

    private static String validCreateOrderJson() {
        return """
                {
                  "userId": "%s",
                  "items": [
                    {
                      "productId": "%s",
                      "productName": "Keyboard",
                      "unitPrice": 10.50,
                      "quantity": 2
                    },
                    {
                      "productId": "%s",
                      "productName": "Mouse",
                      "unitPrice": 25.00,
                      "quantity": 1
                    }
                  ]
                }
                """.formatted(USER_ID, FIRST_PRODUCT_ID, SECOND_PRODUCT_ID);
    }
}
