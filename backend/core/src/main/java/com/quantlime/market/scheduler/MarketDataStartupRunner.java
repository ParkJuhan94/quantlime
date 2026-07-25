package com.quantlime.market.scheduler;

import com.quantlime.common.util.SafeExecutor;
import com.quantlime.market.service.MarketDataRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 서버가 매일 16:00(OhlcvCollectorScheduler)에 항상 떠있는 게 아니라서
 * (로컬 개발 환경처럼 필요할 때만 띄우는 경우), 그 시각에 서버가 꺼져
 * 있었으면 그날의 가격/스코어 갱신이 통째로 스킵되고 아무도 감지하지
 * 못한 채 넘어갔었다(CLAUDE.md §10, 2026-07-16 발견).
 *
 * <p>이전(StartupCatchUpRunner)에는 기준 종목(삼성전자) 하나만 보고
 * "오래됐으면 전체를 다시 돌린다"는 휴리스틱이었는데, 지금은
 * {@link MarketDataRefreshService}가 종목별로 실제 결측 구간만 gap-fill하므로
 * 휴리스틱 없이 기동 시마다 항상 트리거해도 된다 - 이미 최신인 종목은
 * 내부적으로 즉시 스킵되고, 오래 걸리는 건 실제로 채울 게 있는 종목뿐이다.
 * 국내/해외를 별도 스레드에서 병렬 실행하는 것도 서비스 내부에서 처리한다
 * (MarketDataRefreshTaskExecutorConfig 참고).
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class MarketDataStartupRunner implements ApplicationRunner {

    private final MarketDataRefreshService marketDataRefreshService;
    private final TaskExecutor marketDataStartupTaskExecutor;

    @Override
    public void run(ApplicationArguments args) {
        log.info("기동 시 가격/스코어 갭필을 비동기로 트리거합니다");
        marketDataStartupTaskExecutor.execute(() ->
            SafeExecutor.runSafely("기동 시 가격/스코어 갭필", marketDataRefreshService::refreshAll));
    }
}
