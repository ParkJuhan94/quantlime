package com.quantlab.watchlist.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 관심 종목 등록 직후의 이력 백필/스코어 재계산은 외부 API 호출(Toss 캔들
 * 페이지네이션, 퀀트 엔진)을 포함해 수 초가 걸릴 수 있다. 등록 응답을 그
 * 시간만큼 붙잡지 않도록 별도 스레드 풀에서 실행한다.
 */
@Configuration
public class WatchlistTaskExecutorConfig {

    @Bean
    public TaskExecutor watchlistTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("watchlist-async-");
        executor.initialize();
        return executor;
    }
}
