package com.quantlab.infra.toss;

import com.quantlab.common.exception.ExternalApiException;
import com.quantlab.common.util.ExternalApiInvoker;
import com.quantlab.infra.toss.dto.TossCandleResponse;
import com.quantlab.infra.toss.dto.TossExchangeRateResponse;
import com.quantlab.infra.toss.dto.TossMarketCalendarResponse;
import com.quantlab.infra.toss.dto.TossPriceResponse;
import com.quantlab.infra.toss.exception.TossApiErrorCode;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossApiClient {

    private final RestClient tossRestClient;
    private final TossTokenManager tokenManager;

    public TossCandleResponse getDailyCandles(String symbol, int count, String before) {
        return withTokenRetry(token -> ExternalApiInvoker.call(
            TossApiErrorCode.CANDLE_INQUIRY_FAILED,
            () -> tossRestClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .path("/api/v1/candles")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", "1d")
                        .queryParam("count", count)
                        .queryParam("adjusted", true);
                    if (before != null) {
                        builder.queryParam("before", before);
                    }
                    return builder.build();
                })
                .header("authorization", "Bearer " + token)
                .retrieve()
                .body(TossCandleResponse.class),
            HttpClientErrorException.TooManyRequests.class,
            TossApiErrorCode.RATE_LIMIT_EXCEEDED));
    }

    public TossPriceResponse getCurrentPrices(String symbols) {
        return withTokenRetry(token -> ExternalApiInvoker.call(
            TossApiErrorCode.PRICE_INQUIRY_FAILED,
            () -> tossRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/prices")
                    .queryParam("symbols", symbols)
                    .build())
                .header("authorization", "Bearer " + token)
                .retrieve()
                .body(TossPriceResponse.class),
            HttpClientErrorException.TooManyRequests.class,
            TossApiErrorCode.RATE_LIMIT_EXCEEDED));
    }

    /**
     * 환율 조회. Rate Limits Group이 시세 조회(MARKET_DATA)와 분리된
     * MARKET_INFO라 별도 예산을 쓴다(장 운영 캘린더와 동일 그룹) -
     * 호출 측(MarketIndexCache)이 짧게 캐싱해 재호출을 줄인다.
     */
    public TossExchangeRateResponse getExchangeRate(String baseCurrency, String quoteCurrency) {
        return withTokenRetry(token -> ExternalApiInvoker.call(
            TossApiErrorCode.EXCHANGE_RATE_INQUIRY_FAILED,
            () -> tossRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/exchange-rate")
                    .queryParam("baseCurrency", baseCurrency)
                    .queryParam("quoteCurrency", quoteCurrency)
                    .build())
                .header("authorization", "Bearer " + token)
                .retrieve()
                .body(TossExchangeRateResponse.class)));
    }

    /**
     * 국내 장 운영 캘린더 조회(휴장일 포함). Rate Limits Group이 시세
     * 조회(MARKET_DATA)와 분리된 MARKET_INFO라 별도 예산을 쓴다 - 호출
     * 측(MarketCalendarCache)이 하루 1회만 호출하도록 캐싱한다.
     */
    public TossMarketCalendarResponse getMarketCalendar() {
        return withTokenRetry(token -> ExternalApiInvoker.call(
            TossApiErrorCode.MARKET_CALENDAR_INQUIRY_FAILED,
            () -> tossRestClient.get()
                .uri("/api/v1/market-calendar/KR")
                .header("authorization", "Bearer " + token)
                .retrieve()
                .body(TossMarketCalendarResponse.class)));
    }

    /**
     * 토스 API 토큰은 계정당 1개만 유효해 다른 프로세스가 재발급하면 이
     * 인스턴스가 캐싱해둔 토큰이 TTL과 무관하게 조용히 무효화될 수 있다.
     * 401(invalid-token) 응답을 그 신호로 보고 캐시를 지운 뒤 새로 발급받은
     * 토큰으로 1회만 재시도한다(그래도 실패하면 그대로 전파).
     */
    private <T> T withTokenRetry(Function<String, T> apiCall) {
        String token = tokenManager.getAccessToken();
        try {
            return apiCall.apply(token);
        } catch (ExternalApiException e) {
            if (!(e.getCause() instanceof HttpClientErrorException.Unauthorized)) {
                throw e;
            }
            log.warn("토스증권 API 토큰이 무효화된 것으로 감지, 재발급 후 1회 재시도");
            tokenManager.invalidateToken();
            return apiCall.apply(tokenManager.getAccessToken());
        }
    }
}
