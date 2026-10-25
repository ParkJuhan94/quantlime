package com.quantlime.tradingflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 약 26분 걸리는 관리자 수동 백필을 요청 스레드가 아니라 백그라운드에서 돌리는 전용 단일 스레드 실행기. 큐를 두지 않아
 * 이미 백필이 돌고 있으면 새 요청은 거절된다(락과 별개로 이중 방어).
 */
@Configuration
public class StockInvestorTradingTaskExecutorConfig {

    @Bean
    public TaskExecutor stockInvestorTradingBackfillTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("stock-investor-trading-backfill-");
        executor.initialize();
        return executor;
    }
}
