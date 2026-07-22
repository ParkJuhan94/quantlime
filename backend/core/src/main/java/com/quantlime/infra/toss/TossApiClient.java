package com.quantlime.infra.toss;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.infra.toss.dto.TossExchangeRateResponse;
import com.quantlime.infra.toss.dto.TossMarketCalendarResponse;
import com.quantlime.infra.toss.dto.TossPriceResponse;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

    // Toss 호출 결과를 endpoint/outcome 태그로 계측한다 - 특히 rate_limited
    // 발생률이 이 프로젝트가 실제로 겪은 429 장애(CLAUDE.md 2026-07-16
    // 재설계 근거)를 숫자로 드러내는 핵심 지표라 처음부터 붙여둔다.
    private static final String METRIC_CALLS = "toss.api.calls";
    private static final String METRIC_DURATION = "toss.api.duration";
    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_RATE_LIMITED = "rate_limited";
    private static final String OUTCOME_TOKEN_INVALID = "token_invalid";
    private static final String OUTCOME_ERROR = "error";

    private final RestClient tossRestClient;
    private final TossTokenManager tokenManager;
    private final MeterRegistry meterRegistry;

    public TossCandleResponse getDailyCandles(String symbol, int count, String before) {
        return withTokenRetry("candles", token -> ExternalApiInvoker.call(
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
        return withTokenRetry("prices", token -> ExternalApiInvoker.call(
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
        return withTokenRetry("exchange-rate", token -> ExternalApiInvoker.call(
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
        return withTokenRetry("market-calendar", token -> ExternalApiInvoker.call(
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
     *
     * <p>호출 1건(재시도 포함) 전체를 하나의 Timer 샘플로 재고, 최종 결과에
     * 따라 endpoint/outcome 태그를 붙인 Counter를 증가시킨다.
     */
    private <T> T withTokenRetry(String endpoint, Function<String, T> apiCall) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String token = tokenManager.getAccessToken();
            T result = apiCall.apply(token);
            recordOutcome(endpoint, OUTCOME_SUCCESS, sample);
            return result;
        } catch (ExternalApiException e) {
            if (!(e.getCause() instanceof HttpClientErrorException.Unauthorized)) {
                recordOutcome(endpoint, outcomeOf(e), sample);
                throw e;
            }
            log.warn("토스증권 API 토큰이 무효화된 것으로 감지, 재발급 후 1회 재시도");
            tokenManager.invalidateToken();
            try {
                T result = apiCall.apply(tokenManager.getAccessToken());
                recordOutcome(endpoint, OUTCOME_SUCCESS, sample);
                return result;
            } catch (ExternalApiException retryFailure) {
                recordOutcome(endpoint, outcomeOf(retryFailure), sample);
                throw retryFailure;
            }
        }
    }

    private String outcomeOf(ExternalApiException e) {
        if (TossApiErrorCode.RATE_LIMIT_EXCEEDED.getCode().equals(e.getCode())) {
            return OUTCOME_RATE_LIMITED;
        }
        if (e.getCause() instanceof HttpClientErrorException.Unauthorized) {
            return OUTCOME_TOKEN_INVALID;
        }
        return OUTCOME_ERROR;
    }

    private void recordOutcome(String endpoint, String outcome, Timer.Sample sample) {
        meterRegistry.counter(METRIC_CALLS, "endpoint", endpoint, "outcome", outcome).increment();
        sample.stop(meterRegistry.timer(METRIC_DURATION, "endpoint", endpoint, "outcome", outcome));
    }
}
