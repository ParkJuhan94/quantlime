package com.quantlime.tradingflow.service;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.price.implement.DailyPriceReader;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import com.quantlime.tradingflow.implement.StockInvestorTradingCollector;
import com.quantlime.tradingflow.implement.StockInvestorTradingCollector.UpsertSummary;
import com.quantlime.tradingflow.implement.StockInvestorTradingReader;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

/**
 * 국내 전 종목의 투자자 매매동향을 갱신·백필한다. 종목마다 토스를 한 번(백필은 100건당 한 번)씩 순차 호출하고, 호출 간
 * 간격은 {@code TossApiClient}의 전용 페이서가 지킨다. 종목은 3개월 거래대금 내림차순으로 돌려 도중에 끊겨도 대형주가
 * 먼저 반영되게 한다(전종목 가격 갱신과 같은 기준). 한 종목이 실패해도 나머지는 계속한다.
 *
 * <p>가격 갱신 fan-out(15:36)에 넣지 않고 {@link #DAILY_RUN_TIME} 이후 별도 실행으로 둔 이유: 토스의 확정치는 당일
 * 저녁에 반영돼 15:36에는 개인·기타법인이 null인 잠정치뿐이고, 가격 배치(약 22분)의 임계 경로를 늘리고 싶지 않았다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockInvestorTradingRefreshService {

    /** 평일 이 시각 이후에는 그날의 확정치가 반영돼 있다고 보고 갱신한다(스케줄러 cron과 같은 값). */
    public static final LocalTime DAILY_RUN_TIME = LocalTime.of(18, 30);

    static final String LOCK_KEY = "lock:stock-investor-trading-refresh";
    // 전종목 갱신은 약 7분, 400건 백필은 약 26분이라 여유를 둔다. 갱신과 백필이 같은 락을 써서 동시에 돌지 않는다.
    static final Duration LOCK_TTL = Duration.ofHours(3);
    // 외국인 보유는 다음 영업일 오전에 한 번 더 바뀔 수 있고 잠정→확정 전환도 있어, 매번 최근 20거래일을 덮어쓴다.
    // 서버 야간 중지 등으로 빠진 날도 이 윈도가 다음 실행에서 메운다.
    static final int RECENT_COUNT = 20;
    private static final long TRADING_VALUE_LOOKBACK_MONTHS = 3;
    private static final int PROGRESS_LOG_INTERVAL = 200;
    // 연속으로 이만큼 실패하면 토스 쪽 장애/서킷 오픈/네트워크 단절로 보고 남은 종목을 헛돌리지 않고 중단한다 - 로컬에서
    // 절전 복귀 직후 서킷이 열려 남은 약 1,900종목이 20초 만에 전부 실패 처리된 적이 있다. 이미 쌓은 행은 그대로 남고
    // 백필은 이어받기(이미 충분한 종목은 건너뜀)로 다시 호출하면 된다.
    static final int MAX_CONSECUTIVE_FAILURES = 30;

    private final StockMasterService stockMasterService;
    private final DailyPriceReader dailyPriceReader;
    private final StockInvestorTradingCollector stockInvestorTradingCollector;
    private final StockInvestorTradingReader stockInvestorTradingReader;
    private final RedisLockService redisLockService;
    private final Clock clock;
    // 이름이 같은 빈(StockInvestorTradingTaskExecutorConfig)이 주입된다 - TaskExecutor 빈이 여러 개라 필드명으로 구분한다.
    private final TaskExecutor stockInvestorTradingBackfillTaskExecutor;

    /** 락을 잡은 채로만 전 종목 최근 구간 갱신을 실행한다. 이미 다른 실행이 락을 쥐고 있으면 빈 Optional. */
    public Optional<RefreshResult> refreshAllExclusively() {
        return redisLockService.runExclusively(LOCK_KEY, LOCK_TTL,
            () -> runAll("최근 구간 갱신", code -> stockInvestorTradingCollector.refreshRecent(code, RECENT_COUNT)));
    }

    /** 락을 잡은 채로만 전 종목 과거 이력을 {@code days}건까지 백필한다(관리자 수동 트리거). */
    public Optional<RefreshResult> backfillAllExclusively(int days) {
        return redisLockService.runExclusively(LOCK_KEY, LOCK_TTL,
            () -> runAll("이력 백필(" + days + "건)", code -> stockInvestorTradingCollector.backfill(code, days)));
    }

    /**
     * 관리자 수동 트리거용 - 전 종목 최근 구간 갱신(약 7분)을 백그라운드에서 시작한다. 이미 돌고 있으면 false(락이
     * 중복 실행을 한 번 더 막는다).
     */
    public boolean startRefreshAsync() {
        return submit("관리자 수동 갱신", this::refreshAllExclusively);
    }

    /** 관리자 수동 트리거용 - 전 종목 과거 이력 백필(400건이면 약 26분)을 백그라운드에서 시작한다. */
    public boolean startBackfillAsync(int days) {
        return submit("관리자 수동 백필", () -> backfillAllExclusively(days));
    }

    private boolean submit(String label, Supplier<Optional<RefreshResult>> task) {
        try {
            stockInvestorTradingBackfillTaskExecutor.execute(() -> {
                try {
                    task.get().ifPresentOrElse(
                        result -> log.info("종목별 투자자 매매동향 {} 완료: {}", label, result),
                        () -> log.info("이미 다른 실행이 종목별 투자자 매매동향을 처리 중 - {} 스킵", label));
                } catch (Exception e) {
                    log.error("종목별 투자자 매매동향 {} 실패: reason={}", label, e.getMessage(), e);
                }
            });
            return true;
        } catch (TaskRejectedException e) {
            log.warn("종목별 투자자 매매동향 {} 거절: 이미 실행 중", label);
            return false;
        }
    }

    /**
     * 서버가 꺼져 있던 동안 놓친 갱신을 기동 시 따라잡는다 - 저장된 가장 최근 거래일이 "마지막으로 갱신됐어야 할
     * 평일"보다 오래됐으면 최근 구간을 갱신한다. 휴장일에는 그 평일의 데이터가 없어 재기동마다 한 번 더 돌 수 있지만
     * 같은 {@code updatedAt}이면 저장을 건너뛰는 멱등 작업이다.
     */
    public Optional<RefreshResult> refreshIfMissed() {
        LocalDate expected = lastExpectedTradeDate(LocalDateTime.now(clock));
        Optional<LocalDate> latest = stockInvestorTradingReader.findLatestTradeDate();
        if (latest.isPresent() && !latest.get().isBefore(expected)) {
            log.info("종목별 투자자 매매동향 캐치업 불필요: latest={}, expected={}", latest.get(), expected);
            return Optional.empty();
        }
        log.info("종목별 투자자 매매동향 캐치업 실행: latest={}, expected={}", latest.orElse(null), expected);
        return refreshAllExclusively();
    }

    /** {@code now} 시점에 갱신이 끝났어야 할 가장 최근 평일(18:30 이후면 오늘, 아니면 직전 평일). */
    static LocalDate lastExpectedTradeDate(LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        if (now.toLocalTime().isBefore(DAILY_RUN_TIME)) {
            date = date.minusDays(1);
        }
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.minusDays(1);
        }
        return date;
    }

    private RefreshResult runAll(String label, Function<String, UpsertSummary> perStock) {
        List<String> stockCodes = listDomesticStockCodesByTradingValueDesc();
        log.info("종목별 투자자 매매동향 {} 시작: 대상종목수={}", label, stockCodes.size());
        UpsertSummary total = new UpsertSummary(0, 0, 0);
        int failed = 0;
        int processed = 0;
        int consecutiveFailures = 0;
        boolean aborted = false;
        for (String stockCode : stockCodes) {
            try {
                total = total.plus(perStock.apply(stockCode));
                consecutiveFailures = 0;
            } catch (Exception e) {
                failed++;
                consecutiveFailures++;
                log.warn("종목별 투자자 매매동향 {} 실패(해당 종목만 스킵): stockCode={}, error={}",
                    label, stockCode, e.getMessage());
            }
            processed++;
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                log.error("종목별 투자자 매매동향 {} 중단: 연속 {}종목 실패(토스 장애·서킷 오픈·네트워크 단절 의심), 처리={}/{}",
                    label, consecutiveFailures, processed, stockCodes.size());
                aborted = true;
                break;
            }
            if (processed % PROGRESS_LOG_INTERVAL == 0) {
                log.info("종목별 투자자 매매동향 {} 진행: {}/{}", label, processed, stockCodes.size());
            }
        }
        RefreshResult result = new RefreshResult(stockCodes.size(), failed, total.saved(), total.updated(), aborted);
        log.info("종목별 투자자 매매동향 {} {}: 대상={}, 처리={}, 실패={}, 신규저장={}건, 갱신={}건",
            label, aborted ? "중단" : "완료", result.targetCount(), processed, result.failedCount(),
            result.savedRows(), result.updatedRows());
        return result;
    }

    private List<String> listDomesticStockCodesByTradingValueDesc() {
        List<Stock> domestic = stockMasterService.getAllListedStocks().stream()
            .filter(stock -> stock.getMarketType().isDomestic())
            .filter(stock -> !stock.isPriceUnsupported())
            .toList();
        List<String> rankedCodesDesc = dailyPriceReader.findDomesticCodesByTradingValueDesc(
            LocalDate.now(clock).minusMonths(TRADING_VALUE_LOOKBACK_MONTHS));
        Map<String, Integer> rankByCode = new HashMap<>();
        for (int i = 0; i < rankedCodesDesc.size(); i++) {
            rankByCode.put(rankedCodesDesc.get(i), i);
        }
        List<String> codes = new ArrayList<>(domestic.stream().map(Stock::getStockCode).toList());
        codes.sort(Comparator.comparingInt(code -> rankByCode.getOrDefault(code, Integer.MAX_VALUE)));
        return codes;
    }

    /** 전 종목 순회 한 번의 결과. {@code aborted}는 연속 실패로 중간에 멈췄다는 뜻이다. */
    public record RefreshResult(int targetCount, int failedCount, int savedRows, int updatedRows, boolean aborted) {
    }
}
