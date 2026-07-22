package com.quantlime.market.cache;

import com.quantlime.infra.upbit.UpbitApiClient;
import com.quantlime.infra.upbit.dto.UpbitMinuteCandle;
import com.quantlime.market.dto.response.IndexMinuteChartResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 홈 화면 비트코인 카드의 "최근 24시간" 차트 - Upbit 30분봉 48개(=24시간)를
 * 캐싱한다. 단일 마켓(KRW-BTC)만 다뤄 지수처럼 코드별 맵이 아니라 필드
 * 하나로 충분하다(MarketIndexCache와 동일한 단순 TTL 캐시 패턴).
 */
@Component
@RequiredArgsConstructor
public class BitcoinChartCache {

    private static final int TTL_SECONDS = 20;
    private static final String MARKET = "KRW-BTC";
    private static final int UNIT_MINUTES = 30;
    private static final int CANDLE_COUNT = 48;

    private final UpbitApiClient upbitApiClient;

    private volatile List<IndexMinuteChartResponse> cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public List<IndexMinuteChartResponse> get() {
        if (isStale()) {
            refresh();
        }
        return cached;
    }

    private boolean isStale() {
        return cached == null || Duration.between(cachedAt, Instant.now()).getSeconds() >= TTL_SECONDS;
    }

    private synchronized void refresh() {
        if (!isStale()) {
            return; // 락 대기 중 다른 스레드가 이미 갱신함
        }
        List<UpbitMinuteCandle> raw = upbitApiClient.getMinuteCandles(MARKET, UNIT_MINUTES, CANDLE_COUNT);
        cached = raw.stream()
            .map(candle -> new IndexMinuteChartResponse(
                LocalDateTime.parse(candle.candleDateTimeKst()),
                candle.tradePrice()))
            .sorted(Comparator.comparing(IndexMinuteChartResponse::time))
            .toList();
        cachedAt = Instant.now();
    }
}
