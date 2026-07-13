package com.valentin.inventoryservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;


@SpringBootTest
@Testcontainers
class InventoryServiceApplicationTests {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18.4-bookworm");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("inventory_test_db")
            .withUsername("inventory_test_user")
            .withPassword("inventory_test_password");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void contextLoads() {
    }

}
