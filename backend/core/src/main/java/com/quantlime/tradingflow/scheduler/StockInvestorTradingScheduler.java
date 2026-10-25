package com.quantlime.tradingflow.scheduler;

import com.quantlime.tradingflow.service.StockInvestorTradingRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 평일 18:30에 국내 전 종목의 투자자 매매동향을 갱신한다(약 7분). 토스의 확정치는 당일 저녁에 반영되고 서버 야간
 * 중지(00:00~07:40) 전에 끝나며, 20:20 알림·21:00 구독 갱신과도 겹치지 않는다. 이 시각은
 * {@link StockInvestorTradingRefreshService#DAILY_RUN_TIME}과 같아야 한다(기동 캐치업이 같은 값을 쓴다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockInvestorTradingScheduler {

    private final StockInvestorTradingRefreshService stockInvestorTradingRefreshService;

    @Scheduled(cron = "0 30 18 * * MON-FRI", zone = "Asia/Seoul")
    public void refreshDaily() {
        try {
            stockInvestorTradingRefreshService.refreshAllExclusively().ifPresentOrElse(
                result -> log.info("종목별 투자자 매매동향 일 갱신 완료: {}", result),
                () -> log.info("이미 다른 실행이 종목별 투자자 매매동향을 갱신 중 - 이번 실행은 스킵"));
        } catch (Exception e) {
            log.error("종목별 투자자 매매동향 일 갱신 실패: reason={}", e.getMessage(), e);
        }
    }
}
