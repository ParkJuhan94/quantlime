package com.quantlime.price.scheduler;

import com.quantlime.market.service.MarketDataRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 15:36(정규장 마감 후, 월~금) 전 상장종목(국내+해외)의 가격+스코어를 갱신하는
 * 하루 한 번짜리 배치. 실제 갭필 로직은 {@link MarketDataRefreshService}(트리거1)에
 * 위임한다 - dev 수동 트리거(/dev/refresh)·기동 시 캐치업(StartupCatchUpRunner)과
 * 완전히 동일한 로직과 락(refreshAllExclusively)을 공유한다.
 *
 * <p>2026-10-09부로 16:00(잠정)+20:10(확정) 2단계에서 15:36 한 번으로 줄였다 - 스코어와
 * 사분면 변화 알림을 NXT 애프터마켓(거래량이 적음)이 아니라 정규장 기준으로 판단하려는
 * 결정이다. 종가(close)는 15:35에 {@code DomesticRegularCloseCaptureScheduler}가 Redis
 * 시세 스냅샷으로 정규장 마감가를 확정해 두므로(수 초, Toss 호출 없음) 그 직후인 15:36에
 * 시작한다. 스코어의 종가 기반 지표(RSI/MACD/이평/볼린저)는 이 값을 쓴다.
 *
 * <p>시가/고가/저가/거래량은 Toss 일봉이 NXT를 포함하는 값이다. 국내 약 2,600종목을
 * 거래대금 순으로 처리하는 데 약 22분(분당 ~120종목)이 걸려 15:40 애프터마켓 시작 전에
 * 끝낼 수 없다 - 상위 약 480종목만 15:40 전에 처리되고, 나머지는 처리 시각까지의 NXT
 * 체결이 섞인 미확정 값이다. 다음 거래일 배치가 재확정 윈도우({@link
 * com.quantlime.price.util.DailyPriceSettlementPolicy})로 확정값을 덮어쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OhlcvCollectorScheduler {

    private final MarketDataRefreshService marketDataRefreshService;

    @Scheduled(cron = "0 36 15 * * MON-FRI", zone = "Asia/Seoul")
    public void collectDailyOhlcv() {
        runRefresh("정규장 마감 후 수집");
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
