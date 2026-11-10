package com.valentin.orderservice;

import com.valentin.inventory.grpc.InventoryQueryServiceGrpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ImportGrpcClients(
        target = "inventory",
        types = InventoryQueryServiceGrpc.InventoryQueryServiceBlockingStub.class
)
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

}
