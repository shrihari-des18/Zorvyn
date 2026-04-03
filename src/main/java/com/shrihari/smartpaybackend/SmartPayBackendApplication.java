package com.shrihari.smartpaybackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartPayBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartPayBackendApplication.class, args);
    }

}
