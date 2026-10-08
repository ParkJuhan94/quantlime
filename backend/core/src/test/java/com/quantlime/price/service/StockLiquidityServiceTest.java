package com.quantlime.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.price.domain.StockLiquidity;
import com.quantlime.price.dto.LiquiditySnapshot;
import com.quantlime.price.implement.DailyPriceReader;
import com.quantlime.price.implement.StockLiquidityAppender;
import com.quantlime.price.implement.StockLiquidityReader;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 유동성 컷 판정 경계값 검증 - 국내(10억원)/해외(100만 달러) 임계값과 거래정지(거래량 0인 날 5일 이상) 기준.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockLiquidityServiceTest {

    private static final LocalDate SINCE = LocalDate.of(2026, 9, 1);

    @Mock
    private DailyPriceReader dailyPriceReader;

    @Mock
    private StockLiquidityReader stockLiquidityReader;

    @Mock
    private StockLiquidityAppender stockLiquidityAppender;

    @InjectMocks
    private StockLiquidityService service;

    @BeforeEach
    void setUp() {
        // @Value 기본값과 동일하게 맞춘다(단위 테스트라 스프링 주입이 없다)
        ReflectionTestUtils.setField(service, "minAvgTradingValueDomestic", 1_000_000_000d);
        ReflectionTestUtils.setField(service, "minAvgTradingValueOverseas", 1_000_000d);
        ReflectionTestUtils.setField(service, "maxZeroVolumeDays", 5);
    }

    private StockLiquidity savedFor(String stockCode) {
        ArgumentCaptor<StockLiquidity> captor = ArgumentCaptor.forClass(StockLiquidity.class);
        verify(stockLiquidityAppender).save(captor.capture());
        StockLiquidity saved = captor.getValue();
        assertThat(saved.getStockCode()).isEqualTo(stockCode);
        return saved;
    }

    @Test
    @DisplayName("[국내 - 거래대금이 임계값 이상이고 거래정지일이 5일 미만이면 유동성 있음]")
    void refreshDomestic_meetsThresholds_isLiquid() {
        given(dailyPriceReader.findDomesticLiquiditySnapshot(SINCE))
            .willReturn(List.of(new LiquiditySnapshot("005930", 1_000_000_000d, 4L)));
        given(stockLiquidityReader.findByStockCode("005930")).willReturn(Optional.empty());

        service.refreshDomestic(SINCE);

        assertThat(savedFor("005930").isLiquid()).isTrue();
    }

    @Test
    @DisplayName("[국내 - 거래대금이 임계값 미만이면 유동성 없음]")
    void refreshDomestic_belowTradingValue_isIlliquid() {
        given(dailyPriceReader.findDomesticLiquiditySnapshot(SINCE))
            .willReturn(List.of(new LiquiditySnapshot("005930", 999_999_999d, 0L)));
        given(stockLiquidityReader.findByStockCode("005930")).willReturn(Optional.empty());

        service.refreshDomestic(SINCE);

        assertThat(savedFor("005930").isLiquid()).isFalse();
    }

    @Test
    @DisplayName("[거래량 0인 날이 5일 이상이면 거래대금이 커도 유동성 없음(거래정지 간주)]")
    void refreshDomestic_zeroVolumeDaysAtLimit_isIlliquid() {
        given(dailyPriceReader.findDomesticLiquiditySnapshot(SINCE))
            .willReturn(List.of(new LiquiditySnapshot("005930", 9_000_000_000d, 5L)));
        given(stockLiquidityReader.findByStockCode("005930")).willReturn(Optional.empty());

        service.refreshDomestic(SINCE);

        assertThat(savedFor("005930").isLiquid()).isFalse();
    }

    @Test
    @DisplayName("[평균 거래대금이나 거래정지일 수가 null이면 각각 유동성 없음/0일로 처리한다]")
    void refreshDomestic_nullValues_areHandled() {
        given(dailyPriceReader.findDomesticLiquiditySnapshot(SINCE))
            .willReturn(List.of(new LiquiditySnapshot("005930", null, null)));
        given(stockLiquidityReader.findByStockCode("005930")).willReturn(Optional.empty());

        service.refreshDomestic(SINCE);

        StockLiquidity saved = savedFor("005930");
        assertThat(saved.isLiquid()).isFalse();
        assertThat(saved.getZeroVolumeDays20d()).isZero();
    }

    @Test
    @DisplayName("[이미 스냅샷이 있는 종목은 새로 저장하지 않고 기존 행을 갱신한다]")
    void refreshDomestic_existing_isUpdatedInPlace() {
        StockLiquidity existing = StockLiquidity.of("005930", SINCE.minusDays(1), 1d, 9, false);
        given(dailyPriceReader.findDomesticLiquiditySnapshot(SINCE))
            .willReturn(List.of(new LiquiditySnapshot("005930", 2_000_000_000d, 0L)));
        given(stockLiquidityReader.findByStockCode("005930")).willReturn(Optional.of(existing));

        service.refreshDomestic(SINCE);

        assertThat(existing.isLiquid()).isTrue();
        assertThat(existing.getAvgTradingValue20d()).isEqualTo(2_000_000_000d);
        assertThat(existing.getZeroVolumeDays20d()).isZero();
        verify(stockLiquidityAppender, never()).save(any(StockLiquidity.class));
    }

    @Test
    @DisplayName("[해외 - 100만 달러 기준으로 판정한다]")
    void refreshOverseas_usesOverseasThreshold() {
        given(dailyPriceReader.findOverseasLiquiditySnapshot(SINCE))
            .willReturn(List.of(new LiquiditySnapshot("AAPL", 1_000_000d, 0L)));
        given(stockLiquidityReader.findByStockCode("AAPL")).willReturn(Optional.empty());

        service.refreshOverseas(SINCE);

        assertThat(savedFor("AAPL").isLiquid()).isTrue();
    }
}
