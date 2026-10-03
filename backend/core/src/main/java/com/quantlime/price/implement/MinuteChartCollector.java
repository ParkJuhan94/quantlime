package com.quantlime.price.implement;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.price.dto.response.MinuteChartResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Toss 1분봉을 가져와 차트 응답 모양으로 변환하는 구현 레이어(Implementation) - 외부 호출·
 * 응답 변환·페이지네이션 커서 정규화를 이 컴포넌트가 맡고, {@code StockMinuteChartService}는
 * 종목 확인과 캐싱만 한다. 호출당 최대 {@value #PAGE_SIZE}개(토스 API 제약, 약 3.3시간 분량).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinuteChartCollector {

    public static final int PAGE_SIZE = 200;

    private final TossApiClient tossApiClient;

    /**
     * @param before null이면 가장 최근부터. 값이 있으면 UTC {@code Z} 표기 시각 직전부터 과거 방향
     *               (토스 쿼리스트링의 {@code +}가 공백으로 해석되는 문제 때문에 {@code +09:00} 오프셋 금지 -
     *               {@link TossApiClient#get1MinuteCandleBefore} 참고)
     */
    public MinuteChartResponse fetch(String symbol, String before) {
        TossCandleResponse response = tossApiClient.getMinuteCandles(symbol, PAGE_SIZE, before);
        return toResponse(response);
    }

    private MinuteChartResponse toResponse(TossCandleResponse response) {
        if (response == null || response.result() == null || response.result().candles() == null) {
            return new MinuteChartResponse(List.of(), null);
        }
        List<MinuteChartResponse.Candle> candles = response.result().candles().stream()
            .map(candle -> new MinuteChartResponse.Candle(
                OffsetDateTime.parse(candle.timestamp()).toEpochSecond(),
                Double.parseDouble(candle.openPrice()),
                Double.parseDouble(candle.highPrice()),
                Double.parseDouble(candle.lowPrice()),
                Double.parseDouble(candle.closePrice()),
                Double.parseDouble(candle.volume())))
            .sorted(Comparator.comparingLong(MinuteChartResponse.Candle::time))
            .toList();
        return new MinuteChartResponse(candles, toUtcCursor(response.result().nextBefore()));
    }

    // 토스가 돌려주는 nextBefore는 +09:00 같은 오프셋을 가질 수 있어, 그대로 다음 요청의
    // before로 쓰면 +가 공백으로 해석돼 깨진다 - 항상 UTC Z 표기로 정규화해 내려준다.
    private String toUtcCursor(String nextBefore) {
        if (nextBefore == null || nextBefore.isBlank()) {
            return null;
        }
        try {
            return Instant.from(OffsetDateTime.parse(nextBefore)).toString();
        } catch (DateTimeParseException e) {
            log.warn("분봉 nextBefore 파싱 실패, 페이지네이션 종료로 처리: nextBefore={}", nextBefore);
            return null;
        }
    }
}
