package com.quantlime.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.infra.toss.dto.TossCandleResponse.TossCandle;
import com.quantlime.infra.toss.dto.TossCandleResponse.TossCandlePageResult;
import com.quantlime.price.cache.MinuteChartCacheStore;
import com.quantlime.price.dto.response.MinuteChartResponse;
import com.quantlime.price.implement.MinuteChartCollector;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.service.StockMasterService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockMinuteChartServiceTest {

    private static final String CODE = "005930";

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private TossApiClient tossApiClient;

    @Mock
    private MinuteChartCacheStore minuteChartCacheStore;

    // 종목 확인·캐싱(service)과 토스 호출·변환(collector)을 함께 검증한다 - collector는 실제 객체.
    private StockMinuteChartService service;

    @BeforeEach
    void setUp() {
        service = new StockMinuteChartService(
            stockMasterService, new MinuteChartCollector(tossApiClient), minuteChartCacheStore);
        given(stockMasterService.getStockByCode(CODE)).willReturn(StockFixture.createStock(CODE, "삼성전자"));
    }

    private TossCandle candle(String timestamp, String close) {
        return new TossCandle(timestamp, "100", "110", "90", close, "5", "KRW");
    }

    @Test
    @DisplayName("[캐시 미스면 토스를 호출해 시간 오름차순 epoch초 캔들과 UTC Z 커서로 변환하고 캐싱한다]")
    void getMinuteChart_cacheMiss_callsTossAndNormalizes() {
        // given: 토스는 최신순, nextBefore는 +09:00 오프셋
        given(minuteChartCacheStore.find(CODE, null)).willReturn(Optional.empty());
        given(tossApiClient.getMinuteCandles(CODE, 200, null)).willReturn(new TossCandleResponse(
            new TossCandlePageResult(
                List.of(candle("2026-09-01T15:29:00+09:00", "102"), candle("2026-09-01T15:28:00+09:00", "101")),
                "2026-09-01T15:28:00+09:00")));

        // when
        MinuteChartResponse response = service.getMinuteChart(CODE, null);

        // then
        assertThat(response.candles()).extracting(MinuteChartResponse.Candle::close).containsExactly(101.0, 102.0);
        assertThat(response.candles().get(0).time()).isLessThan(response.candles().get(1).time());
        assertThat(response.nextBefore()).isEqualTo("2026-09-01T06:28:00Z");
        verify(minuteChartCacheStore).save(eq(CODE), eq(null), any(MinuteChartResponse.class));
    }

    @Test
    @DisplayName("[캐시 히트면 토스를 호출하지 않는다]")
    void getMinuteChart_cacheHit_skipsToss() {
        // given
        MinuteChartResponse cached = new MinuteChartResponse(List.of(), null);
        given(minuteChartCacheStore.find(CODE, "2026-09-01T06:28:00Z")).willReturn(Optional.of(cached));

        // when
        MinuteChartResponse response = service.getMinuteChart(CODE, "2026-09-01T06:28:00Z");

        // then
        assertThat(response).isSameAs(cached);
        verify(tossApiClient, never()).getMinuteCandles(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    @DisplayName("[토스 응답이 비어 있으면 빈 캔들과 null 커서를 반환한다]")
    void getMinuteChart_emptyResponse_returnsEmpty() {
        // given
        given(minuteChartCacheStore.find(CODE, null)).willReturn(Optional.empty());
        given(tossApiClient.getMinuteCandles(CODE, 200, null)).willReturn(new TossCandleResponse(null));

        // when
        MinuteChartResponse response = service.getMinuteChart(CODE, null);

        // then
        assertThat(response.candles()).isEmpty();
        assertThat(response.nextBefore()).isNull();
    }
}
