package com.quantlime.price.scheduler;

import com.quantlime.common.util.SafeExecutor;
import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.repository.DailyPriceRepository;
import java.time.LocalDate;
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
 * 있었으면 그날의 OHLCV 수집·스코어 재계산 배치가 통째로 스킵되고
 * 아무도 감지하지 못한 채 넘어갔었다(CLAUDE.md §10, 2026-07-16 발견).
 *
 * <p>기동 시점마다 기준 종목(삼성전자, 상장폐지될 가능성이 사실상 없는
 * 대형주)의 최신 OHLCV 날짜를 확인해, 최근 며칠 안에 갱신되지 않았으면
 * 캐치업 배치를 비동기로 트리거한다. 정확한 거래일 캘린더 대조 대신
 * "최근 {@value #STALE_THRESHOLD_DAYS}일 이내 갱신 여부"라는 단순한
 * 휴리스틱을 쓴다 - 주말(2일)+공휴일 1일 정도까지는 여유를 두면서도,
 * 그보다 오래 비어있으면 확실히 이상 신호로 본다. OHLCV 수집(종목당
 * upsert 성격)과 스코어 재계산 모두 재실행에 안전하다(idempotent).
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class StartupCatchUpRunner implements ApplicationRunner {

    private static final String ANCHOR_STOCK_CODE = "005930";
    private static final int STALE_THRESHOLD_DAYS = 4;

    private final DailyPriceRepository dailyPriceRepository;
    private final OhlcvCollectorScheduler ohlcvCollectorScheduler;
    private final TaskExecutor startupCatchUpTaskExecutor;

    @Override
    public void run(ApplicationArguments args) {
        LocalDate latestTradeDate = dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(ANCHOR_STOCK_CODE)
            .map(DailyPrice::getTradeDate)
            .orElse(null);

        if (latestTradeDate != null && !latestTradeDate.isBefore(LocalDate.now().minusDays(STALE_THRESHOLD_DAYS))) {
            log.info("OHLCV/스코어 캐치업 불필요: 최신데이터={}", latestTradeDate);
            return;
        }

        log.warn("OHLCV/스코어 데이터가 오래돼(최신데이터={}) 캐치업 배치를 비동기로 트리거합니다", latestTradeDate);
        startupCatchUpTaskExecutor.execute(() ->
            SafeExecutor.runSafely("기동 시 OHLCV/스코어 캐치업", ohlcvCollectorScheduler::collectDailyOhlcv));
    }
}
