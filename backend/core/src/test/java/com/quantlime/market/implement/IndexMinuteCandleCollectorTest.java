package com.quantlime.market.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossMarketIndicatorCandleResponse;
import com.quantlime.price.dto.response.MinuteChartResponse;
import java.time.OffsetDateTime;
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
class IndexMinuteCandleCollectorTest {

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private IndexMinuteCandleCollector collector;

    @Test
    @DisplayName("[지수 1분봉도 종료 시각 timestamp라 1분을 빼서 시작 시각으로 내려준다 - 코스피 첫 봉 09:01은 09:00 봉]")
    void fetch_shiftsEndTimestampToBarStart() {
        given(tossApiClient.getMarketIndicatorCandles("KOSPI", "1m", 200, null)).willReturn(
            new TossMarketIndicatorCandleResponse(new TossMarketIndicatorCandleResponse.MarketIndicatorCandlePageResult(
                List.of(new TossMarketIndicatorCandleResponse.MarketIndicatorCandle(
                    "2026-10-23T09:01:00+09:00", "5547.07", "5550.3", "5545.27", "5550.3", "20586000")),
                null)));

        MinuteChartResponse result = collector.fetch("KOSPI", null);

        assertThat(result.candles().get(0).time())
            .isEqualTo(OffsetDateTime.parse("2026-10-23T09:00:00+09:00").toEpochSecond());
        assertThat(result.nextBefore()).isNull();
    }
}
