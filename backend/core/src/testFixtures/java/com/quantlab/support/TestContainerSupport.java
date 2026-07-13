package com.quantlab.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class TestContainerSupport {

    private static final int REDIS_PORT = 6379;

    protected static final MySQLContainer<?> MYSQL_CONTAINER;
    protected static final GenericContainer<?> REDIS_CONTAINER;

    static {
        MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("quantlab_test")
            .withUsername("test")
            .withPassword("test");
        MYSQL_CONTAINER.start();

        REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void overrideContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(REDIS_PORT));
    }
}
