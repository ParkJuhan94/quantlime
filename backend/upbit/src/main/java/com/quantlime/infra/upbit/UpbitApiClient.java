package com.quantlime.infra.upbit;

import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.infra.upbit.dto.UpbitCandle;
import com.quantlime.infra.upbit.dto.UpbitMinuteCandle;
import com.quantlime.infra.upbit.dto.UpbitTicker;
import com.quantlime.infra.upbit.exception.UpbitApiErrorCode;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
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

    @Retry(name = "upbit")
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
    @Retry(name = "upbit")
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

    /**
     * 비트코인 상세 차트용 OHLCV 캔들 - {@code candlePath}는 {@code minutes/5}, {@code days}, {@code weeks},
     * {@code months} 중 하나(호출측이 고정 목록에서 고른다). 최신순(내림차순)으로 최대 200개가 내려오고,
     * {@code to}(UTC {@code Z} 표기)를 주면 그 시각 직전부터 과거 방향으로 조회한다.
     */
    @Retry(name = "upbit")
    @CircuitBreaker(name = "upbit")
    @Bulkhead(name = "upbit")
    public List<UpbitCandle> getCandles(String market, String candlePath, int count, @Nullable String to) {
        return ExternalApiInvoker.call(
            UpbitApiErrorCode.CANDLE_INQUIRY_FAILED,
            () -> {
                UpbitCandle[] candles = upbitRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/v1/candles/" + candlePath)
                            .queryParam("market", market)
                            .queryParam("count", count);
                        if (to != null) {
                            uriBuilder.queryParam("to", to);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(UpbitCandle[].class);
                return candles == null ? null : Arrays.asList(candles);
            });
    }
}
