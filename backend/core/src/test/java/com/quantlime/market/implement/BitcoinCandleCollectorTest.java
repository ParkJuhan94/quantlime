package com.quantlime.market.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.quantlime.infra.upbit.UpbitApiClient;
import com.quantlime.infra.upbit.dto.UpbitCandle;
import com.quantlime.price.dto.response.MinuteChartResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BitcoinCandleCollectorTest {

    @Mock
    private UpbitApiClient upbitApiClient;

    @InjectMocks
    private BitcoinCandleCollector collector;

    private UpbitCandle candle(String utc) {
        return new UpbitCandle(utc, 100.0, 110.0, 90.0, 105.0, 3.0);
    }

    @Test
    @DisplayName("[업비트 최신순 응답을 epoch 초 오름차순 OHLCV로 변환한다]")
    void fetch_convertsToAscendingEpochSecondCandles() {
        given(upbitApiClient.getCandles("KRW-BTC", "minutes/5", 200, null)).willReturn(List.of(
            candle("2026-10-25T05:05:00"), candle("2026-10-25T05:00:00")));

        MinuteChartResponse result = collector.fetch("5m", null);

        assertThat(result.candles()).extracting(MinuteChartResponse.Candle::time)
            .containsExactly(1_792_904_400L, 1_792_904_700L);
        assertThat(result.candles().get(0).open()).isEqualTo(100.0);
        assertThat(result.candles().get(0).volume()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("[한 페이지를 다 채우면 가장 오래된 봉 시각이 초까지 포함된 UTC Z 커서가 된다]")
    void fetch_fullPage_returnsCursorWithSeconds() {
        List<UpbitCandle> page = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            // 최신순: 05:00부터 하루씩 과거로
            page.add(candle("2026-10-25T05:00:00"));
        }
        page.set(199, candle("2026-10-24T05:00:00"));
        given(upbitApiClient.getCandles("KRW-BTC", "days", 200, null)).willReturn(page);

        MinuteChartResponse result = collector.fetch("1d", null);

        assertThat(result.nextBefore()).isEqualTo("2026-10-24T05:00:00Z");
    }

    @Test
    @DisplayName("[한 페이지를 못 채우면 더 과거 데이터가 없어 커서가 null이다]")
    void fetch_partialPage_returnsNullCursor() {
        given(upbitApiClient.getCandles("KRW-BTC", "months", 200, "2026-01-01T00:00:00Z"))
            .willReturn(List.of(candle("2025-12-01T00:00:00")));

        MinuteChartResponse result = collector.fetch("1M", "2026-01-01T00:00:00Z");

        assertThat(result.nextBefore()).isNull();
    }

    @Test
    @DisplayName("[지원하지 않는 단위는 업비트를 호출하지 않고 거절한다]")
    void fetch_unknownInterval_throws() {
        assertThatThrownBy(() -> collector.fetch("7m", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
