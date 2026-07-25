package com.quantlime.price.scheduler;

import com.quantlime.common.util.SafeExecutor;
import com.quantlime.market.service.MarketDataRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 16:00(장 마감 후, 월~금) 전 상장종목(국내+해외)의 가격+스코어를
 * 갱신하는 배치. 실제 갭필 로직은 {@link MarketDataRefreshService}(트리거1)에
 * 위임한다 - 이전에는 이 스케줄러가 "고정 10일 재조회 후 오늘자 스코어만
 * 재계산"이라는 자체 로직을 갖고 있었지만, 지금은 dev 수동 트리거
 * (/dev/refresh)·기동 시 캐치업(MarketDataStartupRunner)과 완전히 동일한
 * 로직을 공유한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OhlcvCollectorScheduler {

    private final MarketDataRefreshService marketDataRefreshService;

    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Asia/Seoul")
    public void collectDailyOhlcv() {
        SafeExecutor.runSafely("전종목 가격/스코어 갱신", marketDataRefreshService::refreshAll);
    }
}
