package com.quantlime.common.startup;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@link StartupCatchUpRunner}가 애플리케이션 기동 스레드를 직접 막지 않도록
 * 감싸는 전용 실행기 2개. 가격/스코어 갭필(내부적으로 join 대기)과 영상 피드
 * 캐치업(수집→자막→요약→보존기간 정리)은 서로 다른 외부 API/도메인이라
 * 풀을 분리해 서로를 대기시키지 않게 한다({@code MarketDataRefreshTaskExecutorConfig}의
 * 국내/해외 분리와 동일한 이유). 영상 피드 쪽 4단계는 순차 의존 관계라
 * 하나의 풀에서 순서대로 실행해도 문제없고(보존기간 정리는 삭제 대상이
 * 없으면 즉시 끝나는 가벼운 작업이라 별도 풀을 줄 필요가 없다), 오히려
 * 풀을 쪼갤수록 상시 대기하는 스레드 수만 늘어난다.
 */
@Configuration
public class StartupCatchUpTaskExecutorConfig {

    @Bean
    public TaskExecutor marketDataCatchUpTaskExecutor() {
        return singleThreadExecutor("market-data-catchup-");
    }

    @Bean
    public TaskExecutor videoFeedCatchUpTaskExecutor() {
        return singleThreadExecutor("video-feed-catchup-");
    }

    // 텔레그램 피드(Phase 8 P7)는 유튜브와 서로 다른 외부 API(t.me 스크래핑 vs
    // YouTube Data API)를 호출하는 별도 도메인이라 전용 풀을 분리한다(위
    // videoFeedCatchUpTaskExecutor와 동일 이유).
    @Bean
    public TaskExecutor telegramFeedCatchUpTaskExecutor() {
        return singleThreadExecutor("telegram-feed-catchup-");
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
