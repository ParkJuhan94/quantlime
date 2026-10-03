package com.quantlime.price.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.dto.DomesticStockTradingValue;
import com.quantlime.price.dto.LiquiditySnapshot;
import com.quantlime.support.DataJpaTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("integration")
class DomesticDailyPriceQueryRepositoryImplTest extends DataJpaTestSupport {

    private static final String STOCK_CODE = "005930";

    @Autowired
    private DomesticDailyPriceRepository domesticDailyPriceRepository;

    @Test
    @DisplayName("[당일 행이 이미 저장돼 있어도 전일 종가는 그 이전 최신 값이다]")
    void findLatestBeforeDate_todayAlreadyCollected_returnsPreviousTradingDayClose() {
        // given: 장중 캐치업 수집으로 당일(오늘) 행이 이미 들어와 있는 상황을 재현
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        domesticDailyPriceRepository.save(candle(yesterday, 70000L));
        domesticDailyPriceRepository.save(candle(today, 71500L));

        // when
        List<DomesticDailyPrice> result = domesticDailyPriceRepository.findLatestBeforeDate(
            List.of(STOCK_CODE), today);

        // then: 당일(71500)이 아니라 전일 종가(70000)를 반환해야 한다
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTradeDate()).isEqualTo(yesterday);
        assertThat(result.get(0).getClosePrice()).isEqualTo(70000L);
    }

    @Test
    @DisplayName("[당일 행이 없으면 가장 최근 저장된 행을 반환한다]")
    void findLatestBeforeDate_noTodayRow_returnsLatestStored() {
        // given
        LocalDate today = LocalDate.now();
        domesticDailyPriceRepository.save(candle(today.minusDays(3), 68000L));
        domesticDailyPriceRepository.save(candle(today.minusDays(1), 70000L));

        // when
        List<DomesticDailyPrice> result = domesticDailyPriceRepository.findLatestBeforeDate(
            List.of(STOCK_CODE), today);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTradeDate()).isEqualTo(today.minusDays(1));
        assertThat(result.get(0).getClosePrice()).isEqualTo(70000L);
    }

    // 기간(since) 안에서 종목별 거래대금(종가 x 거래량) 합을 쓰는 3개 쿼리의 공통 시드:
    //  A: 어제 100x1000=100,000 + 그제 거래량 0일(거래대금 0)  /  B: 어제 50x10000=500,000
    //  C: since 이전(10일 전)의 거대한 거래대금 - 기간 밖이라 모든 결과에서 빠져야 한다
    private LocalDate seedTradingValues() {
        LocalDate today = LocalDate.now();
        domesticDailyPriceRepository.save(DomesticDailyPrice.of("A", today.minusDays(1), 100L, 100L, 100L, 100L, 1000L));
        domesticDailyPriceRepository.save(DomesticDailyPrice.of("A", today.minusDays(2), 100L, 100L, 100L, 100L, 0L));
        domesticDailyPriceRepository.save(DomesticDailyPrice.of("B", today.minusDays(1), 50L, 50L, 50L, 50L, 10000L));
        domesticDailyPriceRepository.save(
            DomesticDailyPrice.of("C", today.minusDays(10), 9999L, 9999L, 9999L, 9999L, 9_999_999L));
        return today.minusDays(5);
    }

    @Test
    @DisplayName("[거래대금 상위 조회는 기간 안 종가x거래량 합 내림차순이고 limit만큼 자르며 기간 밖 종목은 제외한다]")
    void findTopByTradingValue_ordersByValueDesc_limitsAndExcludesOutOfRange() {
        LocalDate since = seedTradingValues();

        List<DomesticStockTradingValue> top2 = domesticDailyPriceRepository.findTopByTradingValue(since, 2);
        List<DomesticStockTradingValue> top1 = domesticDailyPriceRepository.findTopByTradingValue(since, 1);

        assertThat(top2).extracting(DomesticStockTradingValue::stockCode).containsExactly("B", "A");
        assertThat(top2).extracting(DomesticStockTradingValue::tradingValue).containsExactly(500_000L, 100_000L);
        assertThat(top1).extracting(DomesticStockTradingValue::stockCode).containsExactly("B");
    }

    @Test
    @DisplayName("[거래대금 순 종목코드 조회는 같은 정렬을 코드만 돌려주고 기간 밖 종목은 없다]")
    void findStockCodesOrderedByTradingValueDesc_returnsCodesInOrder() {
        LocalDate since = seedTradingValues();

        assertThat(domesticDailyPriceRepository.findStockCodesOrderedByTradingValueDesc(since))
            .containsExactly("B", "A");
    }

    @Test
    @DisplayName("[유동성 스냅샷은 종목별 일평균 거래대금과 거래량 0인 날 수를 기간 안에서만 집계한다]")
    void findLiquiditySnapshot_aggregatesAvgTradingValueAndZeroVolumeDays() {
        LocalDate since = seedTradingValues();

        List<LiquiditySnapshot> snapshots = domesticDailyPriceRepository.findLiquiditySnapshot(since);

        assertThat(snapshots).extracting(LiquiditySnapshot::stockCode).containsExactlyInAnyOrder("A", "B");
        LiquiditySnapshot a = snapshots.stream().filter(s -> s.stockCode().equals("A")).findFirst().orElseThrow();
        LiquiditySnapshot b = snapshots.stream().filter(s -> s.stockCode().equals("B")).findFirst().orElseThrow();
        assertThat(a.avgTradingValue()).isEqualTo(50_000.0); // (100,000 + 0) / 2
        assertThat(a.zeroVolumeDays()).isEqualTo(1L);
        assertThat(b.avgTradingValue()).isEqualTo(500_000.0);
        assertThat(b.zeroVolumeDays()).isZero();
    }

    @Test
    @DisplayName("[전일 종가 조회에 종목코드가 비어 있으면 쿼리 없이 빈 목록이다]")
    void findLatestBeforeDate_emptyCodes_returnsEmpty() {
        assertThat(domesticDailyPriceRepository.findLatestBeforeDate(List.of(), LocalDate.now())).isEmpty();
    }

    @Test
    @DisplayName("[전일 종가는 종목마다 각자의 직전 거래일 행을 돌려준다]")
    void findLatestBeforeDate_multipleStocks_returnsEachLatest() {
        LocalDate today = LocalDate.now();
        domesticDailyPriceRepository.save(DomesticDailyPrice.of("A", today.minusDays(1), 1L, 1L, 1L, 11L, 1L));
        domesticDailyPriceRepository.save(DomesticDailyPrice.of("A", today.minusDays(4), 1L, 1L, 1L, 99L, 1L));
        domesticDailyPriceRepository.save(DomesticDailyPrice.of("B", today.minusDays(3), 1L, 1L, 1L, 22L, 1L));

        List<DomesticDailyPrice> result = domesticDailyPriceRepository.findLatestBeforeDate(List.of("A", "B"), today);

        assertThat(result).extracting(DomesticDailyPrice::getStockCode, DomesticDailyPrice::getClosePrice)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("A", 11L), org.assertj.core.groups.Tuple.tuple("B", 22L));
    }

    private DomesticDailyPrice candle(LocalDate tradeDate, long closePrice) {
        return DomesticDailyPrice.of(STOCK_CODE, tradeDate, closePrice, closePrice, closePrice, closePrice, 1000000L);
    }
}
