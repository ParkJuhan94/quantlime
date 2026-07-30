package com.quantlime.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.DailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PriceGapFillServiceTest {

    private static final String STOCK_CODE = "005930";
    private static final String OVERSEAS_STOCK_CODE = "AAPL";

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @Mock
    private OverseasDailyPriceRepository overseasDailyPriceRepository;

    @Mock
    private DailyPriceService dailyPriceService;

    @Mock
    private OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;

    @InjectMocks
    private PriceGapFillService priceGapFillService;

    @Test
    @DisplayName("[저장된 이력이 없으면 국내는 깊은 백필로 최초 적재한다]")
    void fillDomesticGap_noExistingHistory_deepBackfill() {
        // given
        given(dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(STOCK_CODE))
            .willReturn(Optional.empty());

        // when
        priceGapFillService.fillDomesticGap(STOCK_CODE);

        // then
        verify(dailyPriceService).backfillHistoryIfNeeded(STOCK_CODE, 200);
        verify(dailyPriceService, never()).collectDailyPrice(any(), anyInt());
    }

    @Test
    @DisplayName("[이미 오늘까지 저장돼 있으면 국내 API를 호출하지 않는다]")
    void fillDomesticGap_alreadyFresh_skipsApiCall() {
        // given
        given(dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(STOCK_CODE))
            .willReturn(Optional.of(dailyPrice(LocalDate.now())));

        // when
        boolean calledApi = priceGapFillService.fillDomesticGap(STOCK_CODE);

        // then: API 호출이 없었으므로 호출측(MarketDataRefreshService)이 종목 간
        // 딜레이를 걸지 않아도 되도록 false를 반환한다
        assertThat(calledApi).isFalse();
        verify(dailyPriceService, never()).collectDailyPrice(any(), anyInt());
        verify(dailyPriceService, never()).backfillHistoryIfNeeded(any(), anyInt());
    }

    @Test
    @DisplayName("[최근 며칠만 비어 있으면 그 갭만큼만 단일 호출로 채운다]")
    void fillDomesticGap_smallGap_collectsExactGapOnly() {
        // given: 5일 전까지만 저장돼 있음
        given(dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(STOCK_CODE))
            .willReturn(Optional.of(dailyPrice(LocalDate.now().minusDays(5))));

        // when
        priceGapFillService.fillDomesticGap(STOCK_CODE);

        // then
        ArgumentCaptor<Integer> lookbackCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(dailyPriceService).collectDailyPrice(eq(STOCK_CODE), lookbackCaptor.capture());
        assertThat(lookbackCaptor.getValue()).isEqualTo(10); // 5일 갭 + 5일 버퍼
        verify(dailyPriceService, never()).backfillHistoryIfNeeded(any(), anyInt());
    }

    @Test
    @DisplayName("[갭이 단일 호출 한도를 넘으면 깊은 백필로 대체한다]")
    void fillDomesticGap_largeGap_fallsBackToDeepBackfill() {
        // given: 1년 넘게 비어있는 종목(장기 다운타임 등)
        given(dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(STOCK_CODE))
            .willReturn(Optional.of(dailyPrice(LocalDate.now().minusDays(400))));

        // when
        priceGapFillService.fillDomesticGap(STOCK_CODE);

        // then
        verify(dailyPriceService).backfillHistoryIfNeeded(STOCK_CODE, 200);
        verify(dailyPriceService, never()).collectDailyPrice(any(), anyInt());
    }

    @Test
    @DisplayName("[해외 종목도 저장된 이력이 없으면 깊은 백필로 최초 적재한다]")
    void fillOverseasGap_noExistingHistory_deepBackfill() {
        // given
        given(overseasDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(OVERSEAS_STOCK_CODE))
            .willReturn(Optional.empty());

        // when
        priceGapFillService.fillOverseasGap(OVERSEAS_STOCK_CODE);

        // then
        verify(overseasDailyPriceBackfillService)
            .backfillHistoryIfNeeded(OVERSEAS_STOCK_CODE, 200);
        verify(overseasDailyPriceBackfillService, never()).collectRecentPrices(any(), anyInt());
    }

    @Test
    @DisplayName("[해외 종목은 갭이 작으면 그 갭만큼만 단일 호출(collectRecentPrices)로 채운다]")
    void fillOverseasGap_smallGap_collectsRecentPricesOnce() {
        // given: 3일 전까지만 저장돼 있음
        given(overseasDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(OVERSEAS_STOCK_CODE))
            .willReturn(Optional.of(overseasDailyPrice(LocalDate.now().minusDays(3))));

        // when
        priceGapFillService.fillOverseasGap(OVERSEAS_STOCK_CODE);

        // then
        ArgumentCaptor<Integer> lookbackCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(overseasDailyPriceBackfillService)
            .collectRecentPrices(eq(OVERSEAS_STOCK_CODE), lookbackCaptor.capture());
        assertThat(lookbackCaptor.getValue()).isEqualTo(8); // 3일 갭 + 5일 버퍼
        verify(overseasDailyPriceBackfillService, never()).backfillHistoryIfNeeded(any(), anyInt());
    }

    @Test
    @DisplayName("[해외 종목도 갭이 단일 호출 한도(200일, 국내와 동일)를 넘으면 깊은 백필로 대체한다]")
    void fillOverseasGap_largeGap_fallsBackToDeepBackfill() {
        // given
        given(overseasDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(OVERSEAS_STOCK_CODE))
            .willReturn(Optional.of(overseasDailyPrice(LocalDate.now().minusDays(400))));

        // when
        priceGapFillService.fillOverseasGap(OVERSEAS_STOCK_CODE);

        // then
        verify(overseasDailyPriceBackfillService)
            .backfillHistoryIfNeeded(OVERSEAS_STOCK_CODE, 200);
        verify(overseasDailyPriceBackfillService, never()).collectRecentPrices(any(), anyInt());
    }

    private DailyPrice dailyPrice(LocalDate tradeDate) {
        return DailyPrice.of(STOCK_CODE, tradeDate, 70000L, 71000L, 69000L, 70500L, 1000000L);
    }

    private OverseasDailyPrice overseasDailyPrice(LocalDate tradeDate) {
        return OverseasDailyPrice.of(OVERSEAS_STOCK_CODE, tradeDate, 150.0, 152.0, 148.0, 151.0, 1000000L);
    }
}
