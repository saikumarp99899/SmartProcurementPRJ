package com.smartprocure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — verifies the Spring application context loads successfully.
 * If this test passes, all beans are wired correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
class SmartProcureApplicationTests {

    @Test
    void contextLoads() {
        // If the context fails to load, this test will throw an exception.
        // No assertions needed — Spring Boot does the validation.
    }
}
