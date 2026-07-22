package com.quantlime.price.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 서버 기동 시 OHLCV/스코어 캐치업 배치(StartupCatchUpRunner)를 돌리는
 * 전용 스레드 풀 - 전종목 수집은 수 분이 걸릴 수 있어(WatchlistTaskExecutorConfig와
 * 동일한 이유) 애플리케이션 기동 자체를 막지 않도록 별도 스레드에서 실행한다.
 * 기동 시 1회만 도는 일회성 작업이라 풀 크기는 최소로 둔다.
 */
@Configuration
public class PriceTaskExecutorConfig {

    @Bean
    public TaskExecutor startupCatchUpTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("startup-catchup-");
        executor.initialize();
        return executor;
    }
}
