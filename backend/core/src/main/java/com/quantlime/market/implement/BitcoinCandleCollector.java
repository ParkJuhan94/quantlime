package com.quantlime.market.implement;

import com.quantlime.infra.upbit.UpbitApiClient;
import com.quantlime.infra.upbit.dto.UpbitCandle;
import com.quantlime.price.dto.response.MinuteChartResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 업비트 KRW-BTC 캔들을 종목 분봉과 같은 차트 응답 모양({@link MinuteChartResponse})으로 변환하는
 * 구현 레이어 - 프론트가 주식 분봉 차트 컴포넌트를 그대로 재사용할 수 있게 하려는 것이다.
 * 업비트가 분·일·주·월봉을 직접 내려주므로 서버에서 집계하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class BitcoinCandleCollector {

    public static final int PAGE_SIZE = 200;
    private static final String MARKET = "KRW-BTC";

    // 컨트롤러 검증 정규식(MarketController)과 같은 집합 - 사용자 입력이 업비트 경로로 그대로 가지 않게 고정 목록으로만 변환한다.
    private static final Map<String, String> CANDLE_PATHS = Map.of(
        "1m", "minutes/1",
        "3m", "minutes/3",
        "5m", "minutes/5",
        "15m", "minutes/15",
        "30m", "minutes/30",
        "60m", "minutes/60",
        "1d", "days",
        "1w", "weeks",
        "1M", "months");

    private final UpbitApiClient upbitApiClient;

    /**
     * 비트코인 캔들 한 페이지를 조회한다.
     *
     * @param before null이면 가장 최근부터. 값이 있으면 UTC {@code Z} 표기 시각 직전부터 과거 방향
     */
    public MinuteChartResponse fetch(String interval, @Nullable String before) {
        String candlePath = CANDLE_PATHS.get(interval);
        if (candlePath == null) {
            throw new IllegalArgumentException("지원하지 않는 비트코인 캔들 단위입니다: " + interval);
        }
        List<UpbitCandle> raw = upbitApiClient.getCandles(MARKET, candlePath, PAGE_SIZE, before);
        if (raw == null || raw.isEmpty()) {
            return new MinuteChartResponse(List.of(), null);
        }

        List<MinuteChartResponse.Candle> candles = raw.stream()
            .map(candle -> new MinuteChartResponse.Candle(
                toEpochSecond(candle.candleDateTimeUtc()),
                candle.openingPrice(),
                candle.highPrice(),
                candle.lowPrice(),
                candle.tradePrice(),
                candle.candleAccTradeVolume()))
            .sorted(Comparator.comparingLong(MinuteChartResponse.Candle::time))
            .toList();

        // 한 페이지를 다 채우지 못했으면 더 과거 데이터가 없다는 뜻이다(업비트는 상장 이전 봉이 없음).
        String nextBefore = raw.size() < PAGE_SIZE ? null : toUtcCursor(candles.get(0).time());
        return new MinuteChartResponse(candles, nextBefore);
    }

    private long toEpochSecond(String candleDateTimeUtc) {
        return LocalDateTime.parse(candleDateTimeUtc).toEpochSecond(ZoneOffset.UTC);
    }

    private String toUtcCursor(long epochSecond) {
        // Instant.toString()은 초가 0이어도 항상 초까지 찍는다(LocalDateTime은 생략해서 커서 정규식에 안 맞는다).
        return Instant.ofEpochSecond(epochSecond).toString();
    }
}
