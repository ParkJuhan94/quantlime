package com.quantlime.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 기본 {@code @Scheduled} 풀 크기는 {@code spring.task.scheduling.pool.size}
 * (application.yml)로 지정한다 - 미지정 시 Spring Boot 기본값이 1이라, 외부
 * 호출 하나가 hang하면(특히 타임아웃 없는 RestClient와 결합 시) {@code @Scheduled}
 * 13개 전부가 그 뒤에서 영구 대기하게 된다(2026-08-17 감사에서 실제로 확인).
 * 그 위험은 {@link HttpClientFactorySupport}(전 RestClient 타임아웃 명시)로
 * 우선 줄였지만, 스케줄러들이 스레드 하나를 공유하는 구조 자체도 함께
 * 고쳐야 한다.
 *
 * <p>고빈도 시세 스윕/릴레이 3개({@code DomesticMarketPriceSweepScheduler}
 * 100ms, {@code DomesticWatchlistPriceRelayScheduler}/{@code
 * OverseasWatchlistPriceScheduler} 3000ms)는 위 공용 풀과도 분리해 이 전용
 * 풀을 쓴다({@code @Scheduled(scheduler = "priceSweepTaskScheduler")}) - 정상
 * 동작 중에도 전종목 스윕이 초당 여러 번 돌아, 공용 풀에 두면 그 틱들이
 * 하루 몇 번뿐인 cron 배치(일봉 수집, 피드 수집 등)를 계속 뒤로 밀어낼 수
 * 있다.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    private static final int PRICE_SWEEP_POOL_SIZE = 2;

    @Bean
    public ThreadPoolTaskScheduler priceSweepTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(PRICE_SWEEP_POOL_SIZE);
        scheduler.setThreadNamePrefix("price-sweep-");
        scheduler.initialize();
        return scheduler;
    }
}
