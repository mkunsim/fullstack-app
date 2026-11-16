package com.example.safari;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SafariBookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(SafariBookingApplication.class, args);
    }
}
