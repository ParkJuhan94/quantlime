package com.quantlime.price.dto.response;

import java.util.List;

/**
 * 분봉 차트 한 페이지. candles는 시간 오름차순이고 time은 epoch 초(UTC) -
 * 프론트 차트 라이브러리가 그대로 쓸 수 있는 형태다. nextBefore는 다음(더 과거)
 * 페이지를 요청할 때 {@code before}로 넘기는 UTC {@code Z} 커서이며, 더 과거 데이터가
 * 없으면 null이다.
 */
public record MinuteChartResponse(
    List<Candle> candles,
    String nextBefore
) {

    public record Candle(
        long time,
        double open,
        double high,
        double low,
        double close,
        double volume
    ) {
    }
}
