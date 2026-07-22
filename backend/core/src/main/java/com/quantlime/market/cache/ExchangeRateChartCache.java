package com.quantlime.market.cache;

import com.quantlime.infra.naver.NaverFinanceApiClient;
import com.quantlime.infra.naver.dto.NaverExchangeRateCandleResponse;
import com.quantlime.market.dto.response.IndexChartResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 홈 화면 달러 환율 카드의 일별 차트 - 원/달러(FX_USDKRW) 단일 페어만
 * 다뤄 지수처럼 코드별 맵이 아니라 필드 하나로 충분하다(BitcoinChartCache와
 * 동일한 단순 TTL 캐시 패턴). 시가/고가/저가는 제공되지 않아 종가를 4개
 * 필드 모두에 채운다 - 미니 차트는 close만 쓰므로 문제 없다.
 */
@Component
@RequiredArgsConstructor
public class ExchangeRateChartCache {

    private static final int TTL_SECONDS = 60;
    private static final String PAIR = "FX_USDKRW";
    private static final int PAGE_SIZE = 60;

    private final NaverFinanceApiClient naverFinanceApiClient;

    private volatile List<IndexChartResponse> cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public List<IndexChartResponse> get() {
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
        List<NaverExchangeRateCandleResponse> raw = naverFinanceApiClient.getExchangeRatePrices(PAIR, PAGE_SIZE);
        cached = raw.stream()
            .map(this::toChartResponse)
            .sorted(Comparator.comparing(IndexChartResponse::tradeDate))
            .toList();
        cachedAt = Instant.now();
    }

    private IndexChartResponse toChartResponse(NaverExchangeRateCandleResponse candle) {
        double close = Double.parseDouble(candle.closePrice().replace(",", ""));
        return new IndexChartResponse(LocalDate.parse(candle.localTradedAt()), close, close, close, close);
    }
}
