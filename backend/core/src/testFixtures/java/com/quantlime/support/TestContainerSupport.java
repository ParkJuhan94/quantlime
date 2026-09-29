package com.quantlime.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class TestContainerSupport {

    private static final int REDIS_PORT = 6379;

    protected static final MySQLContainer<?> MYSQL_CONTAINER;
    protected static final GenericContainer<?> REDIS_CONTAINER;
    // 운영/로컬 docker-compose와 같은 Redpanda 버전 - 브로커가 없으면 KafkaAdmin의
    // 토픽 생성 대기(30s)가 컨텍스트마다 반복돼 CI api:test가 6분 넘게 걸렸다.
    protected static final RedpandaContainer REDPANDA_CONTAINER;

    static {
        MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("quantlime_test")
            .withUsername("test")
            .withPassword("test");
        MYSQL_CONTAINER.start();

        REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);
        REDIS_CONTAINER.start();

        REDPANDA_CONTAINER = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.2.18");
        REDPANDA_CONTAINER.start();
    }

    @DynamicPropertySource
    static void overrideContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(REDIS_PORT));
        registry.add("spring.kafka.bootstrap-servers", REDPANDA_CONTAINER::getBootstrapServers);
    }
}
