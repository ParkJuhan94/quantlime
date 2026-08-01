package com.quantlime.market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@code MarketDataRefreshService}(트리거1: 국내+해외 가격·스코어 갭필)를
 * 국내/해외로 나눠 병렬 실행하는 전용 스레드 풀. 서로 다른 외부 API(Toss/KIS)라
 * 레이트리밋이 독립적이므로 순차로 묶을 이유가 없다 - 각 풀 내부는 종목을
 * 순차 처리한다(동시 호출은 그 API 자체의 레이트리밋을 넘길 뿐 도움이 안 됨).
 */
@Configuration
public class MarketDataRefreshTaskExecutorConfig {

    @Bean
    public TaskExecutor domesticMarketDataRefreshTaskExecutor() {
        return singleThreadExecutor("domestic-refresh-");
    }

    @Bean
    public TaskExecutor overseasMarketDataRefreshTaskExecutor() {
        return singleThreadExecutor("overseas-refresh-");
    }

    private TaskExecutor singleThreadExecutor(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
}
