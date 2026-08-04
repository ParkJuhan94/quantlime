package com.quantlime.price.scheduler;

import com.quantlime.market.service.MarketDataRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 16:00(장 마감 후, 월~금) + 20:10(NXT 애프터마켓 마감 후) 전 상장종목
 * (국내+해외)의 가격+스코어를 갱신하는 배치. 실제 갭필 로직은
 * {@link MarketDataRefreshService}(트리거1)에 위임한다 - 이전에는 이
 * 스케줄러가 "고정 10일 재조회 후 오늘자 스코어만 재계산"이라는 자체
 * 로직을 갖고 있었지만, 지금은 dev 수동 트리거(/dev/refresh)·기동 시
 * 캐치업(StartupCatchUpRunner)과 완전히 동일한 로직을 공유한다. 락
 * (refreshAllExclusively)도 이 셋이 공유해 서버가 하필 16:00/20:10
 * 근처에 재기동돼도 기동 캐치업과 겹쳐 돌지 않는다
 * (FeedCollectionScheduler/VideoRetentionScheduler와 동일한 패턴,
 * 2026-08-02).
 *
 * <p>16:00 한 번으로 끝내지 않고 20:10에 한 번 더 도는 이유 - Toss 일봉
 * (interval=1d)은 NXT 프리(08:00~09:00)/애프터(15:30~20:00)마켓을 포함해
 * 20:00까지 계속 갱신되는 값이다(실측: 정규장 개장 전인 08:23에 이미 당일
 * 캔들이 거래량과 함께 존재). 즉 16:00에 저장한 종가는 정의상 미확정
 * 스냅샷이고, 20:10 배치가 그날의 진짜 확정값으로 재확정한다
 * ({@link com.quantlime.price.util.DailyPriceSettlementPolicy} 참고).
 * 16:30에 잠정 스코어를 먼저 보여주고 20:35에 확정값으로 조용히 교체되는
 * 셈이다. `MARKET_DATA_CHART`는 초당 토큰버킷(일일 쿼터 없음)이라 스윕이
 * 하루 2회로 늘어도 무방하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OhlcvCollectorScheduler {

    private final MarketDataRefreshService marketDataRefreshService;

    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Asia/Seoul")
    public void collectDailyOhlcv() {
        runRefresh("장 마감 직후 1차 수집(잠정)");
    }

    /**
     * NXT 애프터마켓 종료(20:00) 직후 한 번 더 돌려 그날 일봉을 확정한다.
     * 재확정 윈도우(DailyPriceSettlementPolicy) 안의 거래일은 이미 저장돼
     * 있어도 최신 응답으로 덮어쓰므로, 이 실행이 16:00 잠정값을 확정값으로
     * 교체한다.
     */
    @Scheduled(cron = "0 10 20 * * MON-FRI", zone = "Asia/Seoul")
    public void settleDailyOhlcv() {
        runRefresh("NXT 마감 후 재확정");
    }

    private void runRefresh(String label) {
        try {
            marketDataRefreshService.refreshAllExclusively().ifPresentOrElse(
                result -> log.info("전종목 가격/스코어 갱신 완료: {}", label),
                () -> log.info("이미 다른 실행이 가격/스코어 갱신 중 - 이번 실행은 스킵: {}", label));
        } catch (Exception e) {
            log.error("전종목 가격/스코어 갱신 실패: {}, reason={}", label, e.getMessage(), e);
        }
    }
}
