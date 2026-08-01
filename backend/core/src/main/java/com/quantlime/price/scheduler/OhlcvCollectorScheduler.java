package com.quantlime.price.scheduler;

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
 * (/dev/refresh)·기동 시 캐치업(StartupCatchUpRunner)과 완전히 동일한
 * 로직을 공유한다. 락(refreshAllExclusively)도 그 셋이 공유해 서버가
 * 하필 16:00 근처에 재기동돼도 기동 캐치업과 겹쳐 돌지 않는다
 * (FeedCollectionScheduler/VideoRetentionScheduler와 동일한 패턴,
 * 2026-08-02).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OhlcvCollectorScheduler {

    private final MarketDataRefreshService marketDataRefreshService;

    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Asia/Seoul")
    public void collectDailyOhlcv() {
        try {
            marketDataRefreshService.refreshAllExclusively().ifPresentOrElse(
                result -> log.info("전종목 가격/스코어 갱신 완료"),
                () -> log.info("이미 다른 실행이 가격/스코어 갱신 중 - 이번 실행은 스킵"));
        } catch (Exception e) {
            log.error("전종목 가격/스코어 갱신 실패: reason={}", e.getMessage(), e);
        }
    }
}
