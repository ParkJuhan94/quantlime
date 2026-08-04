package com.quantlime.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PriceGapFillServiceTest {

    private static final String STOCK_CODE = "005930";
    private static final String OVERSEAS_STOCK_CODE = "AAPL";

    @Mock
    private DomesticDailyPriceRepository domesticDailyPriceRepository;

    @Mock
    private OverseasDailyPriceRepository overseasDailyPriceRepository;

    @Mock
    private DomesticDailyPriceService domesticDailyPriceService;

    @Mock
    private OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;

    @InjectMocks
    private PriceGapFillService priceGapFillService;

    @Test
    @DisplayName("[저장된 이력이 없으면 국내는 깊은 백필로 최초 적재한다]")
    void fillDomesticGap_noExistingHistory_deepBackfill() {
        // given
        given(domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(STOCK_CODE))
            .willReturn(Optional.empty());

        // when
        priceGapFillService.fillDomesticGap(STOCK_CODE);

        // then
        verify(domesticDailyPriceService).backfillHistoryIfNeeded(STOCK_CODE, 200);
        verify(domesticDailyPriceService, never()).refreshRecent(any(), anyInt());
    }

    // 구 테스트명 [이미 오늘까지 저장돼 있으면 국내 API를 호출하지 않는다]를
    // 의미 반전시켰다 - "오늘 행이 있다(갭 없음)"와 "확정됐다(20:00 이후
    // 저장됐다)"를 같은 것으로 취급한 게 장중 스냅샷이 그날의 확정 종가로
    // 영구 고정되는 버그의 원인이었다(2026-08-03 실측: 삼성전자 08:23
    // 프리마켓 조각이 그렇게 고정됨). 이 테스트가 그 버그의 회귀 방지
    // 앵커다.
    @Test
    @DisplayName("[오늘 행이 있어도 확정 시각(20:00) 전이면 다시 조회한다]")
    void fillDomesticGap_todayUnsettled_refetchesEvenWithNoGap() {
        // given: updatedAt이 null(미영속 픽스처) - 미확정으로 취급된다
        given(domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(STOCK_CODE))
            .willReturn(Optional.of(domesticDailyPrice(LocalDate.now())));

        // when
        boolean calledApi = priceGapFillService.fillDomesticGap(STOCK_CODE);

        // then: 갭 0일 + 버퍼 5일 = 5일치를 재조회한다
        assertThat(calledApi).isTrue();
        verify(domesticDailyPriceService).refreshRecent(STOCK_CODE, 5);
        verify(domesticDailyPriceService, never()).backfillHistoryIfNeeded(any(), anyInt());
    }

    @Test
    @DisplayName("[오늘 행이 20:00 이후에 저장됐으면 재조회하지 않는다]")
    void fillDomesticGap_todaySettled_skipsApiCall() {
        // given
        DomesticDailyPrice settled = domesticDailyPrice(LocalDate.now());
        ReflectionTestUtils.setField(settled, "updatedAt",
            LocalDateTime.of(LocalDate.now(), LocalTime.of(20, 5)));
        given(domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(STOCK_CODE))
            .willReturn(Optional.of(settled));

        // when
        boolean calledApi = priceGapFillService.fillDomesticGap(STOCK_CODE);

        // then: API 호출이 없었으므로 호출측(MarketDataRefreshService)이 종목 간
        // 딜레이를 걸지 않아도 되도록 false를 반환한다
        assertThat(calledApi).isFalse();
        verify(domesticDailyPriceService, never()).refreshRecent(any(), anyInt());
        verify(domesticDailyPriceService, never()).backfillHistoryIfNeeded(any(), anyInt());
    }

    @Test
    @DisplayName("[미확정이라도 최근에 이미 재조회했으면 이번 실행은 건너뛴다]")
    void fillDomesticGap_recentlyRefreshed_skipsApiCall() {
        // given: 5분 전에 갱신됨(60분 가드 이내) - 아직 미확정이지만 재기동
        // 가드가 재조회를 막는다
        DomesticDailyPrice recentlyRefreshed = domesticDailyPrice(LocalDate.now());
        ReflectionTestUtils.setField(recentlyRefreshed, "updatedAt", LocalDateTime.now().minusMinutes(5));
        given(domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(STOCK_CODE))
            .willReturn(Optional.of(recentlyRefreshed));

        // when
        boolean calledApi = priceGapFillService.fillDomesticGap(STOCK_CODE);

        // then
        assertThat(calledApi).isFalse();
        verify(domesticDailyPriceService, never()).refreshRecent(any(), anyInt());
    }

    @Test
    @DisplayName("[최근 며칠만 비어 있으면 그 갭만큼만 단일 호출로 채운다]")
    void fillDomesticGap_smallGap_collectsExactGapOnly() {
        // given: 5일 전까지만 저장돼 있음
        given(domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(STOCK_CODE))
            .willReturn(Optional.of(domesticDailyPrice(LocalDate.now().minusDays(5))));

        // when
        priceGapFillService.fillDomesticGap(STOCK_CODE);

        // then
        ArgumentCaptor<Integer> lookbackCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(domesticDailyPriceService).refreshRecent(eq(STOCK_CODE), lookbackCaptor.capture());
        assertThat(lookbackCaptor.getValue()).isEqualTo(10); // 5일 갭 + 5일 버퍼
        verify(domesticDailyPriceService, never()).backfillHistoryIfNeeded(any(), anyInt());
    }

    @Test
    @DisplayName("[갭이 단일 호출 한도를 넘으면 깊은 백필로 대체한다]")
    void fillDomesticGap_largeGap_fallsBackToDeepBackfill() {
        // given: 1년 넘게 비어있는 종목(장기 다운타임 등)
        given(domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(STOCK_CODE))
            .willReturn(Optional.of(domesticDailyPrice(LocalDate.now().minusDays(400))));

        // when
        priceGapFillService.fillDomesticGap(STOCK_CODE);

        // then
        verify(domesticDailyPriceService).backfillHistoryIfNeeded(STOCK_CODE, 200);
        verify(domesticDailyPriceService, never()).refreshRecent(any(), anyInt());
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
        verify(overseasDailyPriceBackfillService, never()).refreshRecent(any(), anyInt());
    }

    @Test
    @DisplayName("[해외 종목은 갭이 작으면 그 갭만큼만 단일 호출(refreshRecent)로 채운다]")
    void fillOverseasGap_smallGap_collectsRecentPricesOnce() {
        // given: 3일 전까지만 저장돼 있음
        given(overseasDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(OVERSEAS_STOCK_CODE))
            .willReturn(Optional.of(overseasDailyPrice(LocalDate.now().minusDays(3))));

        // when
        priceGapFillService.fillOverseasGap(OVERSEAS_STOCK_CODE);

        // then
        ArgumentCaptor<Integer> lookbackCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(overseasDailyPriceBackfillService)
            .refreshRecent(eq(OVERSEAS_STOCK_CODE), lookbackCaptor.capture());
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
        verify(overseasDailyPriceBackfillService, never()).refreshRecent(any(), anyInt());
    }

    private DomesticDailyPrice domesticDailyPrice(LocalDate tradeDate) {
        return DomesticDailyPrice.of(STOCK_CODE, tradeDate, 70000L, 71000L, 69000L, 70500L, 1000000L);
    }

    private OverseasDailyPrice overseasDailyPrice(LocalDate tradeDate) {
        return OverseasDailyPrice.of(OVERSEAS_STOCK_CODE, tradeDate, 150.0, 152.0, 148.0, 151.0, 1000000L);
    }
}
