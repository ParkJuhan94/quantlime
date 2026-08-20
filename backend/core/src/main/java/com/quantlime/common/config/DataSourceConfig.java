package com.quantlime.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * primary(쓰기)/replica(읽기) 2대로 구성됐을 때만 켜지는 읽기·쓰기 라우팅
 * DataSource. {@code app.datasource.replica.enabled=true}가 아니면 이 설정
 * 클래스 자체가 로드되지 않고, Spring Boot의 기본 자동설정(단일
 * {@code spring.datasource.*})이 그대로 적용된다 - 로컬 replica 검증
 * 세션(2026-08-20) 외 환경(테스트/prod)에서 라우팅 관련 리스크를 지지
 * 않기 위함.
 *
 * <p>{@code @Transactional(readOnly = true)}가 이미 서비스 계층 35곳에
 * 붙어 있었지만 라우팅 데이터소스가 없어 지금까지는 아무 효과가 없었다
 * (docs/RELIABILITY.md 참고) - 이 클래스가 그 표시를 실제로 동작시킨다.
 *
 * <p><b>{@link LazyConnectionDataSourceProxy}로 감싸는 게 필수다.</b> 이걸
 * 빼면 스프링이 트랜잭션 시작 시점(readOnly 여부가 결정되기 전)에 커넥션을
 * 먼저 확보해버려 라우팅이 항상 default(WRITE)로 고정된다 - 이 패턴에서
 * 가장 흔한 함정이라 반드시 지킨다.
 *
 * <p>datasource-proxy-spring-boot-starter(SQL 로깅)는 컨텍스트의 모든
 * {@link DataSource} 빈을 감싸는 BeanPostProcessor라, 여기서 노출하는 최종
 * {@link DataSource} 빈 하나만 감싸지도록 내부 write/read
 * {@link HikariDataSource}는 별도 {@code @Bean}으로 등록하지 않고 이
 * 메서드 안에서 직접 생성한다(별도 빈이면 각각 다시 감겨 로그가 중복되거나
 * 레이어가 꼬일 수 있음).
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.datasource.replica", name = "enabled", havingValue = "true")
public class DataSourceConfig {

    private enum Role { WRITE, READ }

    private HikariDataSource writeDataSource;
    private HikariDataSource readDataSource;

    @Bean
    public DataSource dataSource(
            @Value("${spring.datasource.driver-class-name}") String driverClassName,
            @Value("${spring.datasource.url}") String writeUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${app.datasource.replica.url}") String readUrl,
            @Value("${spring.datasource.hikari.maximum-pool-size:10}") int maxPoolSize,
            @Value("${spring.datasource.hikari.connection-timeout:30000}") long connectionTimeoutMs) {

        writeDataSource = buildHikari(
            "quantlime-write", driverClassName, writeUrl, username, password, maxPoolSize, connectionTimeoutMs);
        readDataSource = buildHikari(
            "quantlime-read", driverClassName, readUrl, username, password, maxPoolSize, connectionTimeoutMs);

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(Role.WRITE, writeDataSource);
        targetDataSources.put(Role.READ, readDataSource);

        RoutingDataSource routingDataSource = new RoutingDataSource();
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(writeDataSource);
        routingDataSource.afterPropertiesSet();

        log.info("읽기/쓰기 라우팅 DataSource 활성화: write={}, read={}", writeUrl, readUrl);
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    private HikariDataSource buildHikari(
            String poolName, String driverClassName, String url, String username, String password,
            int maxPoolSize, long connectionTimeoutMs) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setDriverClassName(driverClassName);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setConnectionTimeout(connectionTimeoutMs);
        return new HikariDataSource(config);
    }

    @PreDestroy
    public void closeDataSources() {
        if (writeDataSource != null) {
            writeDataSource.close();
        }
        if (readDataSource != null) {
            readDataSource.close();
        }
    }

    private static class RoutingDataSource extends AbstractRoutingDataSource {
        @Override
        protected Object determineCurrentLookupKey() {
            boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            return readOnly ? Role.READ : Role.WRITE;
        }
    }
}
