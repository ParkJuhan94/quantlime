package com.quantlime.market.implement;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossMarketIndicatorCandleResponse;
import com.quantlime.price.dto.response.MinuteChartResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 코스피/코스닥 1분봉을 토스 시장지표 캔들 API에서 받아 종목 분봉과 같은 응답 모양
 * ({@link MinuteChartResponse})으로 변환하는 구현 레이어 - 종목 분봉 차트 컴포넌트를 그대로 재사용하고,
 * {@code before} 커서로 이전 거래일까지 이어 받을 수 있게 한다(기존 {@code DomesticIndexMinuteChartCache}는
 * 최신 200분 한 페이지 + 종가만이라 홈 카드 용도).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexMinuteCandleCollector {

    public static final int PAGE_SIZE = 200;
    private static final String INTERVAL_MINUTE = "1m";
    // 시장지표 1분봉도 종목처럼 timestamp가 봉 "종료" 시각이다 - 코스피 실데이터에서 첫 봉이 09:01, 마지막 봉이 15:30
    // (정규장 390분)으로 내려오는 걸 확인했다(2026-10-25). 명세 설명("봉 시작 시각")과 달라서 실데이터를 따랐다.
    // 차트 라이브러리는 time을 봉 시작으로 그리므로 1분을 뺀다. 커서(nextBefore)는 그대로 둔다.
    private static final long BAR_DURATION_SECONDS = 60;

    private final TossApiClient tossApiClient;

    /**
     * @param before null이면 가장 최근부터. 값이 있으면 UTC {@code Z} 표기 시각 직전부터 과거 방향
     */
    public MinuteChartResponse fetch(String indexCode, @Nullable String before) {
        TossMarketIndicatorCandleResponse response =
            tossApiClient.getMarketIndicatorCandles(indexCode, INTERVAL_MINUTE, PAGE_SIZE, before);
        if (response == null || response.result() == null || response.result().candles() == null) {
            return new MinuteChartResponse(List.of(), null);
        }
        List<MinuteChartResponse.Candle> candles = response.result().candles().stream()
            .map(candle -> new MinuteChartResponse.Candle(
                OffsetDateTime.parse(candle.timestamp()).toEpochSecond() - BAR_DURATION_SECONDS,
                Double.parseDouble(candle.openPrice()),
                Double.parseDouble(candle.highPrice()),
                Double.parseDouble(candle.lowPrice()),
                Double.parseDouble(candle.closePrice()),
                Double.parseDouble(candle.volume())))
            .sorted(Comparator.comparingLong(MinuteChartResponse.Candle::time))
            .toList();
        return new MinuteChartResponse(candles, toUtcCursor(response.result().nextBefore()));
    }

    // 토스가 돌려주는 nextBefore는 +09:00 같은 오프셋을 가질 수 있어 그대로 다음 요청의 before로 쓰면
    // +가 공백으로 해석돼 깨진다 - 항상 UTC Z 표기로 정규화한다(MinuteChartCollector와 같은 이유).
    @Nullable
    private String toUtcCursor(@Nullable String nextBefore) {
        if (nextBefore == null || nextBefore.isBlank()) {
            return null;
        }
        try {
            return Instant.from(OffsetDateTime.parse(nextBefore)).toString();
        } catch (DateTimeParseException e) {
            log.warn("지수 분봉 nextBefore 파싱 실패, 페이지네이션 종료로 처리: nextBefore={}", nextBefore);
            return null;
        }
    }
}
