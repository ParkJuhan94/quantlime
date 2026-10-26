package com.quantlime.tradingflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.price.implement.DailyPriceReader;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.service.StockMasterService;
import com.quantlime.tradingflow.implement.StockInvestorTradingCollector;
import com.quantlime.tradingflow.implement.StockInvestorTradingCollector.UpsertSummary;
import com.quantlime.tradingflow.implement.StockInvestorTradingReader;
import com.quantlime.tradingflow.service.StockInvestorTradingRefreshService.RefreshResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockInvestorTradingRefreshServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private DailyPriceReader dailyPriceReader;

    @Mock
    private StockInvestorTradingCollector collector;

    @Mock
    private StockInvestorTradingReader reader;

    @Mock
    private RedisLockService redisLockService;

    @Mock
    private TaskExecutor stockInvestorTradingBackfillTaskExecutor;

    private StockInvestorTradingRefreshService service;

    // 2026-10-26(월) 19:00 KST - 18:30 이후라 그날(10-26)이 갱신돼 있어야 한다.
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), KST);

    @BeforeEach
    void setUp() {
        service = new StockInvestorTradingRefreshService(stockMasterService, dailyPriceReader, collector, reader,
            redisLockService, clock, stockInvestorTradingBackfillTaskExecutor);
    }

    @SuppressWarnings("unchecked")
    private void lockAlwaysAcquired() {
        given(redisLockService.runExclusively(any(), any(), any())).willAnswer(invocation -> {
            Supplier<Object> task = invocation.getArgument(2);
            return Optional.of(task.get());
        });
    }

    @Test
    @DisplayName("[18:30 이전이면 직전 평일, 이후면 오늘이 기대 거래일이다]")
    void lastExpectedTradeDate_dependsOnRunTime() {
        // 2026-10-26 월요일
        assertThat(StockInvestorTradingRefreshService.lastExpectedTradeDate(LocalDateTime.of(2026, 10, 26, 18, 29)))
            .isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(StockInvestorTradingRefreshService.lastExpectedTradeDate(LocalDateTime.of(2026, 10, 26, 18, 30)))
            .isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(StockInvestorTradingRefreshService.lastExpectedTradeDate(LocalDateTime.of(2026, 10, 27, 9, 0)))
            .isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    @DisplayName("[주말은 건너뛰어 금요일을 기대 거래일로 본다]")
    void lastExpectedTradeDate_skipsWeekend() {
        // 2026-10-24 토요일, 2026-10-25 일요일
        assertThat(StockInvestorTradingRefreshService.lastExpectedTradeDate(LocalDateTime.of(2026, 10, 24, 12, 0)))
            .isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(StockInvestorTradingRefreshService.lastExpectedTradeDate(LocalDateTime.of(2026, 10, 25, 20, 0)))
            .isEqualTo(LocalDate.of(2026, 10, 23));
    }

    @Test
    @DisplayName("[거래대금 내림차순으로 국내 종목만 돌고 한 종목이 실패해도 나머지는 계속한다]")
    void refreshAll_ordersByTradingValue_andIsolatesFailures() {
        lockAlwaysAcquired();
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(
            StockFixture.createStock("000660", "SK하이닉스"),
            StockFixture.createStock("005930", "삼성전자"),
            StockFixture.createOverseasStock("AAPL", "Apple")));
        given(dailyPriceReader.findDomesticCodesByTradingValueDesc(any())).willReturn(List.of("005930", "000660"));
        given(collector.refreshRecent("005930", 20)).willThrow(new IllegalStateException("toss down"));
        given(collector.refreshRecent("000660", 20)).willReturn(new UpsertSummary(2, 1, 17));

        Optional<RefreshResult> result = service.refreshAllExclusively();

        assertThat(result).contains(new RefreshResult(2, 1, 2, 1, false));
        var order = org.mockito.Mockito.inOrder(collector);
        order.verify(collector).refreshRecent("005930", 20);
        order.verify(collector).refreshRecent("000660", 20);
        verify(collector, never()).refreshRecent(eq("AAPL"), anyInt());
    }

    @Test
    @DisplayName("[연속으로 실패가 이어지면 남은 종목을 돌지 않고 중단해 aborted로 알린다]")
    void refreshAll_consecutiveFailures_abortsEarly() {
        lockAlwaysAcquired();
        int total = StockInvestorTradingRefreshService.MAX_CONSECUTIVE_FAILURES + 20;
        List<com.quantlime.stock.domain.Stock> stocks = new java.util.ArrayList<>();
        for (int i = 0; i < total; i++) {
            stocks.add(StockFixture.createStock(String.format("%06d", i), "종목" + i));
        }
        given(stockMasterService.getAllListedStocks()).willReturn(stocks);
        given(dailyPriceReader.findDomesticCodesByTradingValueDesc(any())).willReturn(List.of());
        given(collector.refreshRecent(any(), anyInt())).willThrow(new IllegalStateException("circuit open"));

        Optional<RefreshResult> result = service.refreshAllExclusively();

        assertThat(result).isPresent();
        assertThat(result.get().aborted()).isTrue();
        assertThat(result.get().failedCount()).isEqualTo(StockInvestorTradingRefreshService.MAX_CONSECUTIVE_FAILURES);
        verify(collector, org.mockito.Mockito.times(StockInvestorTradingRefreshService.MAX_CONSECUTIVE_FAILURES))
            .refreshRecent(any(), anyInt());
    }

    @Test
    @DisplayName("[성공이 끼면 연속 실패 카운트가 초기화돼 중단하지 않는다]")
    void refreshAll_successResetsConsecutiveFailureCount() {
        lockAlwaysAcquired();
        int failuresBeforeSuccess = StockInvestorTradingRefreshService.MAX_CONSECUTIVE_FAILURES - 1;
        List<com.quantlime.stock.domain.Stock> stocks = new java.util.ArrayList<>();
        for (int i = 0; i < failuresBeforeSuccess * 2 + 1; i++) {
            stocks.add(StockFixture.createStock(String.format("%06d", i), "종목" + i));
        }
        given(stockMasterService.getAllListedStocks()).willReturn(stocks);
        given(dailyPriceReader.findDomesticCodesByTradingValueDesc(any())).willReturn(List.of());
        given(collector.refreshRecent(any(), anyInt())).willAnswer(invocation -> {
            int code = Integer.parseInt(invocation.getArgument(0));
            if (code == failuresBeforeSuccess) {
                return new UpsertSummary(1, 0, 0);
            }
            throw new IllegalStateException("fail");
        });

        Optional<RefreshResult> result = service.refreshAllExclusively();

        assertThat(result.get().aborted()).isFalse();
        assertThat(result.get().failedCount()).isEqualTo(failuresBeforeSuccess * 2);
    }

    @Test
    @DisplayName("[백필은 days를 그대로 콜렉터에 넘긴다]")
    void backfillAll_passesDays() {
        lockAlwaysAcquired();
        given(stockMasterService.getAllListedStocks())
            .willReturn(List.of(StockFixture.createStock("005930", "삼성전자")));
        given(dailyPriceReader.findDomesticCodesByTradingValueDesc(any())).willReturn(List.of("005930"));
        given(collector.backfill("005930", 400)).willReturn(new UpsertSummary(400, 0, 0));

        Optional<RefreshResult> result = service.backfillAllExclusively(400);

        assertThat(result).contains(new RefreshResult(1, 0, 400, 0, false));
    }

    @Test
    @DisplayName("[락을 이미 다른 실행이 쥐고 있으면 아무것도 하지 않는다]")
    void refreshAll_lockHeld_doesNothing() {
        given(redisLockService.runExclusively(any(), any(), any())).willReturn(Optional.empty());

        assertThat(service.refreshAllExclusively()).isEmpty();
        verify(collector, never()).refreshRecent(any(), anyInt());
    }

    @Test
    @DisplayName("[저장된 최근 거래일이 기대 거래일 이상이면 캐치업하지 않는다]")
    void refreshIfMissed_upToDate_skips() {
        given(reader.findLatestTradeDate()).willReturn(Optional.of(LocalDate.of(2026, 10, 26)));

        assertThat(service.refreshIfMissed()).isEmpty();
        verify(redisLockService, never()).runExclusively(any(), any(), any());
    }

    @Test
    @DisplayName("[저장된 데이터가 없거나 오래됐으면 최근 구간을 갱신한다]")
    void refreshIfMissed_stale_refreshes() {
        lockAlwaysAcquired();
        given(reader.findLatestTradeDate()).willReturn(Optional.of(LocalDate.of(2026, 10, 22)));
        given(stockMasterService.getAllListedStocks()).willReturn(List.of());
        given(dailyPriceReader.findDomesticCodesByTradingValueDesc(any())).willReturn(List.of());

        assertThat(service.refreshIfMissed()).contains(new RefreshResult(0, 0, 0, 0, false));
    }

    @Test
    @DisplayName("[비동기 시작은 실행기에 제출하고, 이미 실행 중이라 거절되면 false다]")
    void startBackfillAsync_submitsOrReportsRejection() {
        assertThat(service.startBackfillAsync(400)).isTrue();
        verify(stockInvestorTradingBackfillTaskExecutor).execute(any());

        willThrow(new TaskRejectedException("busy")).given(stockInvestorTradingBackfillTaskExecutor).execute(any());
        assertThat(service.startBackfillAsync(400)).isFalse();
    }
}
