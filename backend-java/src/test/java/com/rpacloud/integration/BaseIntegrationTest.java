package com.rpacloud.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("enterprise")
public abstract class BaseIntegrationTest {

    static final MySQLContainer<?> MYSQL;
    static final GenericContainer<?> REDIS;

    static {
        MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                .withDatabaseName("rpa_cloud_test")
                .withUsername("test")
                .withPassword("test");
        MYSQL.start();

        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        REDIS.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // Quartz in-memory for IT
        registry.add("spring.quartz.job-store-type", () -> "memory");

        // Auth
        registry.add("rpa.auth.disabled", () -> "true");
        registry.add("rpa.auth.secret-key", () -> "integration-test-secret-key-at-least-32-chars!!");

        // Proxy config
        registry.add("rpa.proxy.cooldown-seconds", () -> "1");
        registry.add("rpa.proxy.health-check-interval-ms", () -> "999999999");
        registry.add("rpa.proxy.max-concurrent-checks", () -> "2");
    }
}
