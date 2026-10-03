package com.quantlime.price.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.dto.LiquiditySnapshot;
import com.quantlime.price.dto.OverseasStockTradingValue;
import com.quantlime.support.DataJpaTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 국내 쿼리 테스트와 같은 시드를 달러 가격(Double)으로 옮겨, 해외 쿼리 구현체의 SQL 정확성을 검증한다. */
@Tag("integration")
class OverseasDailyPriceQueryRepositoryImplTest extends DataJpaTestSupport {

    @Autowired
    private OverseasDailyPriceRepository overseasDailyPriceRepository;

    private OverseasDailyPrice candle(String code, LocalDate date, double close, long volume) {
        return OverseasDailyPrice.of(code, date, close, close, close, close, volume);
    }

    // A: 어제 100x1000=100,000 + 그제 거래량 0일 / B: 어제 50x10000=500,000 / C: since 이전(10일 전) 거대 거래대금
    private LocalDate seedTradingValues() {
        LocalDate today = LocalDate.now();
        overseasDailyPriceRepository.save(candle("A", today.minusDays(1), 100.0, 1000L));
        overseasDailyPriceRepository.save(candle("A", today.minusDays(2), 100.0, 0L));
        overseasDailyPriceRepository.save(candle("B", today.minusDays(1), 50.0, 10000L));
        overseasDailyPriceRepository.save(candle("C", today.minusDays(10), 9999.0, 9_999_999L));
        return today.minusDays(5);
    }

    @Test
    @DisplayName("[거래대금 상위 조회는 기간 안 종가x거래량 합 내림차순이고 limit만큼 자르며 기간 밖 종목은 제외한다]")
    void findTopByTradingValue_ordersByValueDesc_limitsAndExcludesOutOfRange() {
        LocalDate since = seedTradingValues();

        List<OverseasStockTradingValue> top2 = overseasDailyPriceRepository.findTopByTradingValue(since, 2);
        List<OverseasStockTradingValue> top1 = overseasDailyPriceRepository.findTopByTradingValue(since, 1);

        assertThat(top2).extracting(OverseasStockTradingValue::stockCode).containsExactly("B", "A");
        assertThat(top2).extracting(OverseasStockTradingValue::tradingValue).containsExactly(500_000.0, 100_000.0);
        assertThat(top1).extracting(OverseasStockTradingValue::stockCode).containsExactly("B");
    }

    @Test
    @DisplayName("[거래대금 순 종목코드 조회는 같은 정렬을 코드만 돌려주고 기간 밖 종목은 없다]")
    void findStockCodesOrderedByTradingValueDesc_returnsCodesInOrder() {
        LocalDate since = seedTradingValues();

        assertThat(overseasDailyPriceRepository.findStockCodesOrderedByTradingValueDesc(since))
            .containsExactly("B", "A");
    }

    @Test
    @DisplayName("[유동성 스냅샷은 종목별 일평균 거래대금과 거래량 0인 날 수를 기간 안에서만 집계한다]")
    void findLiquiditySnapshot_aggregatesAvgTradingValueAndZeroVolumeDays() {
        LocalDate since = seedTradingValues();

        List<LiquiditySnapshot> snapshots = overseasDailyPriceRepository.findLiquiditySnapshot(since);

        assertThat(snapshots).extracting(LiquiditySnapshot::stockCode).containsExactlyInAnyOrder("A", "B");
        LiquiditySnapshot a = snapshots.stream().filter(s -> s.stockCode().equals("A")).findFirst().orElseThrow();
        LiquiditySnapshot b = snapshots.stream().filter(s -> s.stockCode().equals("B")).findFirst().orElseThrow();
        assertThat(a.avgTradingValue()).isEqualTo(50_000.0);
        assertThat(a.zeroVolumeDays()).isEqualTo(1L);
        assertThat(b.avgTradingValue()).isEqualTo(500_000.0);
        assertThat(b.zeroVolumeDays()).isZero();
    }

    @Test
    @DisplayName("[전일 종가는 당일 행이 이미 있어도 그 이전 최신 값이고, 종목마다 각자의 직전 거래일 행을 돌려준다]")
    void findLatestBeforeDate_ignoresTodayAndReturnsEachStocksLatest() {
        LocalDate today = LocalDate.now();
        overseasDailyPriceRepository.save(candle("A", today, 999.0, 1L)); // 당일(제외돼야 함)
        overseasDailyPriceRepository.save(candle("A", today.minusDays(1), 11.0, 1L));
        overseasDailyPriceRepository.save(candle("A", today.minusDays(4), 99.0, 1L));
        overseasDailyPriceRepository.save(candle("B", today.minusDays(3), 22.0, 1L));

        List<OverseasDailyPrice> result = overseasDailyPriceRepository.findLatestBeforeDate(List.of("A", "B"), today);

        assertThat(result).extracting(OverseasDailyPrice::getStockCode, OverseasDailyPrice::getClosePrice)
            .containsExactlyInAnyOrder(Tuple.tuple("A", 11.0), Tuple.tuple("B", 22.0));
    }

    @Test
    @DisplayName("[전일 종가 조회에 종목코드가 비어 있으면 빈 목록이다]")
    void findLatestBeforeDate_emptyCodes_returnsEmpty() {
        assertThat(overseasDailyPriceRepository.findLatestBeforeDate(List.of(), LocalDate.now())).isEmpty();
    }
}
