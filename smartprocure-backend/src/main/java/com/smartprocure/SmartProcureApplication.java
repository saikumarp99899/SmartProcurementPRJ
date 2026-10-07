package com.smartprocure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application entry point.
 *
 * @EnableAsync: enables @Async support for background tasks like audit logging.
 * Without this, @Async methods run synchronously (blocking).
 *
 * @EnableScheduling: enables @Scheduled support for periodic tasks like
 * expiring proxy login sessions that have exceeded 60 minutes.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class SmartProcureApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartProcureApplication.class, args);
    }
}
