package com.quantlab.market.cache;

import com.quantlab.common.exception.NotFoundException;
import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossExchangeRateResponse;
import com.quantlab.infra.upbit.UpbitApiClient;
import com.quantlab.infra.upbit.dto.UpbitTicker;
import com.quantlab.market.dto.response.MarketIndexResponse;
import com.quantlab.market.exception.MarketErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 환율(토스)·비트코인(Upbit) 시세를 짧은 TTL로 캐싱한다. 토스 환율은
 * 스펙상 1분 단위로만 갱신되고, 위젯도 사용자가 페이지를 열 때마다
 * 매번 재조회할 만큼 정밀할 필요가 없어 {@value #TTL_SECONDS}초
 * 캐싱으로 외부 API 호출을 줄인다(MarketCalendarCache와 동일한
 * 단순 TTL 캐시 패턴).
 */
@Component
@RequiredArgsConstructor
public class MarketIndexCache {

    private static final int TTL_SECONDS = 20;
    private static final String USD = "USD";
    private static final String KRW = "KRW";
    private static final String BITCOIN_MARKET = "KRW-BTC";

    private final TossApiClient tossApiClient;
    private final UpbitApiClient upbitApiClient;

    private volatile MarketIndexResponse cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public MarketIndexResponse get() {
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
        TossExchangeRateResponse.ExchangeRateResult exchangeRate =
            tossApiClient.getExchangeRate(USD, KRW).result();
        UpbitTicker bitcoinTicker = getBitcoinTicker();

        cached = new MarketIndexResponse(
            Double.parseDouble(exchangeRate.rate()),
            exchangeRate.rateChangeType(),
            bitcoinTicker.tradePrice(),
            bitcoinTicker.signedChangeRate() * 100
        );
        cachedAt = Instant.now();
    }

    private UpbitTicker getBitcoinTicker() {
        List<UpbitTicker> tickers = upbitApiClient.getTicker(BITCOIN_MARKET);
        return tickers.stream()
            .findFirst()
            .orElseThrow(() -> new NotFoundException(MarketErrorCode.BITCOIN_TICKER_NOT_FOUND));
    }
}
