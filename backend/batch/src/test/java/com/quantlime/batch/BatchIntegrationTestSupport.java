package com.quantlime.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.quantlime.backtest.implement.BacktestReader;
import com.quantlime.backtest.service.BacktestDatasetPreparationService;
import com.quantlime.backtest.service.BacktestService;
import com.quantlime.backtest.service.BacktestUniverseService;
import com.quantlime.backtest.service.CrossSectionalBacktestService;
import com.quantlime.batch.common.BatchExecutionView;
import com.quantlime.batch.common.BatchJobLaunchService;
import com.quantlime.infra.slack.SlackWebhookClient;
import com.quantlime.score.implement.ScoreAppender;
import com.quantlime.score.service.ScoreService;
import com.quantlime.stock.service.StockMasterService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 실제 MySQL(Testcontainers)에 Batch 메타 테이블을 만들어 Job을 끝까지 돌려 보는 통합 테스트의 공통 베이스. core 서비스는
 * 전부 mock이라 quant-engine·Toss·스코어/백테스트 테이블은 쓰지 않는다. {@code @MockBean} 조합을 이 클래스에 모아 서브클래스가
 * 같은 컨텍스트를 재사용하게 한다(조합이 다르면 컨텍스트가 새로 뜬다).
 *
 * <p>컨테이너는 클래스마다 새로 띄우지 않고 JVM에서 한 번만 띄운다 - 컨텍스트가 캐시되는 동안 컨테이너가 바뀌면 안 된다.
 */
@SpringBootTest(classes = BatchIntegrationTestSupport.TestApp.class)
public abstract class BatchIntegrationTestSupport {

    protected static final MySQLContainer<?> MYSQL;

    static {
        MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("quantlime_batch_test").withUsername("test").withPassword("test");
        MYSQL.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.batch.jdbc.initialize-schema", () -> "always");
        registry.add("spring.batch.job.enabled", () -> "false");
    }

    @Configuration
    @EnableAutoConfiguration
    @ComponentScan("com.quantlime.batch")
    static class TestApp {

        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @MockBean
    protected StockMasterService stockMasterService;
    @MockBean
    protected ScoreService scoreService;
    @MockBean
    protected ScoreAppender scoreAppender;
    @MockBean
    protected BacktestDatasetPreparationService datasetPreparationService;
    @MockBean
    protected BacktestService backtestService;
    @MockBean
    protected BacktestUniverseService universeService;
    @MockBean
    protected CrossSectionalBacktestService crossSectionalService;
    @MockBean
    protected BacktestReader backtestReader;
    @MockBean
    protected SlackWebhookClient slackWebhookClient;

    @Autowired
    protected BatchJobLaunchService launchService;

    /** 종료 시각이 저장될 때까지 기다린다 - 상태만 보면 저장 전에 다음 실행을 시작해 "이미 실행 중"으로 오인될 수 있다. */
    protected BatchExecutionView runToEnd(long executionId) {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            BatchExecutionView view = launchService.getExecution(executionId);
            assertThat(view.endTime()).isNotNull();
            assertThat(view.status()).isIn("COMPLETED", "FAILED", "STOPPED");
        });
        return launchService.getExecution(executionId);
    }
}
