package com.quantlab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.quantlab")
public class QuantLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuantLabApplication.class, args);
    }
}
