package com.valentin.orderservice;

import com.valentin.orderservice.client.InventoryClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.service.registry.ImportHttpServices;

@EnableScheduling
@SpringBootApplication
@ImportHttpServices(
        group = "inventory",
        types = InventoryClient.class
)
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

}
