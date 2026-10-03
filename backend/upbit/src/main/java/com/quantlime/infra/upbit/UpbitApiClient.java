package com.quantlime.infra.upbit;

import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.infra.upbit.dto.UpbitMinuteCandle;
import com.quantlime.infra.upbit.dto.UpbitTicker;
import com.quantlime.infra.upbit.exception.UpbitApiErrorCode;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Upbit 공개 시세 API(인증 불필요). 토스증권 Open API가 암호화폐를 다루지
 * 않아 비트코인 위젯만을 위해 별도 연동한다. 진입점마다 {@code @CircuitBreaker}/
 * {@code @Bulkhead}("upbit" 인스턴스) 적용(2026-08-17) - 호출측(MarketIndexCache/
 * BitcoinChartCache)이 이전 캐시값으로 stale-serve한다.
 */
@Component
@RequiredArgsConstructor
public class UpbitApiClient {

    private final RestClient upbitRestClient;

    @CircuitBreaker(name = "upbit")
    @Bulkhead(name = "upbit")
    public List<UpbitTicker> getTicker(String market) {
        return ExternalApiInvoker.call(
            UpbitApiErrorCode.TICKER_INQUIRY_FAILED,
            () -> {
                UpbitTicker[] tickers = upbitRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/v1/ticker")
                        .queryParam("markets", market)
                        .build())
                    .retrieve()
                    .body(UpbitTicker[].class);
                return tickers == null ? null : Arrays.asList(tickers);
            });
    }

    // 홈 카드의 "최근 24시간" 비트코인 차트용 - 30분봉 48개 = 24시간.
    @CircuitBreaker(name = "upbit")
    @Bulkhead(name = "upbit")
    public List<UpbitMinuteCandle> getMinuteCandles(String market, int unitMinutes, int count) {
        return ExternalApiInvoker.call(
            UpbitApiErrorCode.CANDLE_INQUIRY_FAILED,
            () -> {
                UpbitMinuteCandle[] candles = upbitRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/v1/candles/minutes/{unit}")
                        .queryParam("market", market)
                        .queryParam("count", count)
                        .build(unitMinutes))
                    .retrieve()
                    .body(UpbitMinuteCandle[].class);
                return candles == null ? null : Arrays.asList(candles);
            });
    }
}
