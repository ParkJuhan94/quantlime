package com.quantlime.price.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
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
class MinuteChartCollectorTest {

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private MinuteChartCollector collector;

    private TossCandleResponse.TossCandle candle(String timestamp, String close) {
        return new TossCandleResponse.TossCandle(timestamp, "100", "110", "90", close, "5", "KRW");
    }

    @Test
    @DisplayName("[토스 1분봉 timestamp는 종료 시각이라 1분을 빼서 봉 시작 시각으로 내려준다]")
    void fetch_shiftsEndTimestampToBarStart() {
        // given: NXT 프리마켓 첫 봉은 08:00~08:01 구간이 08:01로 내려온다
        String endOf0800Bar = "2026-10-23T08:01:00+09:00";
        given(tossApiClient.getMinuteCandles("005930", 200, null)).willReturn(
            new TossCandleResponse(new TossCandleResponse.TossCandlePageResult(
                List.of(candle(endOf0800Bar, "101")), "2026-10-23T08:00:00+09:00")));

        // when
        MinuteChartResponse result = collector.fetch("005930", null);

        // then
        long expectedStart = OffsetDateTime.parse("2026-10-23T08:00:00+09:00").toEpochSecond();
        assertThat(result.candles().get(0).time()).isEqualTo(expectedStart);
    }

    @Test
    @DisplayName("[다음 페이지 커서는 토스가 준 값을 UTC Z로만 바꾸고 보정하지 않는다]")
    void fetch_cursorIsNotShifted() {
        given(tossApiClient.getMinuteCandles("005930", 200, null)).willReturn(
            new TossCandleResponse(new TossCandleResponse.TossCandlePageResult(
                List.of(candle("2026-10-23T08:01:00+09:00", "101")), "2026-10-23T08:00:00+09:00")));

        MinuteChartResponse result = collector.fetch("005930", null);

        assertThat(result.nextBefore()).isEqualTo("2026-10-22T23:00:00Z");
    }
}
