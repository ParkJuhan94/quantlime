package com.quantlime.infra.toss;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.common.util.SleepUtil;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.infra.toss.dto.TossExchangeRateResponse;
import com.quantlime.infra.toss.dto.TossInvestorTradingResponse;
import com.quantlime.infra.toss.dto.TossMarketCalendarResponse;
import com.quantlime.infra.toss.dto.TossMarketIndicatorCandleResponse;
import com.quantlime.infra.toss.dto.TossMarketIndicatorPriceResponse;
import com.quantlime.infra.toss.dto.TossPriceResponse;
import com.quantlime.infra.toss.dto.TossRankingResponse;
import com.quantlime.infra.toss.dto.TossUsMarketCalendarResponse;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 진입점(public) 메서드마다 {@code @CircuitBreaker}/{@code @Bulkhead}(둘 다
 * {@code "toss"} 인스턴스, application.yml 설정 참고)를 붙인다 - Retry-After
 * 기반 429 재시도(getDailyCandles)/토큰 재발급 재시도(withTokenRetry) 등
 * 기존 커스텀 방어 로직은 프록시가 감싸는 메서드 "안쪽"에서 그대로
 * 동작하므로 건드리지 않는다. 서킷 실패 집계에서 429는
 * {@link com.quantlime.common.resilience.RateLimitAwareFailurePredicate}로
 * 제외해, 레이트리밋을 "서버가 살아있다는 신호"로 계속 다루던 기존 설계와
 * 충돌하지 않게 한다(2026-08-17).
 */
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

    // /api/v1/candles는 MARKET_DATA_CHART 레이트리밋 그룹 하나를 국내(domestic
    // TaskExecutor)/해외(overseas TaskExecutor) 스윕이 동시에 나눠 쓴다
    // (2026-07-29 해외를 KIS에서 이 엔드포인트로 이관하면서 생긴 변화 - 이전엔
    // KIS가 독립된 예산이었음). 각 스레드가 자기 쪽에서만 150ms 딜레이를 두면
    // 두 스레드의 호출이 서로 무관하게 겹쳐 실질 결합 호출률이 그 2배 가까이
    // 치솟아 429가 급증했다(2026-07-30 실측 - 두 스레드의 실패 로그가 수 ms
    // 간격으로 번갈아 찍힘). 이 클라이언트 레벨에서 전역으로 최소 간격을
    // 강제해 몇 개 스레드가 동시에 부르든 결합 호출률이 이 값을 넘지 않게 한다.
    private static final long MIN_CANDLE_CALL_INTERVAL_MS = 150;
    // 전역 페이싱(위 MIN_CANDLE_CALL_INTERVAL_MS)을 거쳐도 순간적으로
    // 429가 날 수 있어 재시도한다(2026-08-01 - 이전엔
    // DomesticDailyPriceService/OverseasDailyPriceBackfillService가 각자
    // fetchCandlesWithRetry로 동일한 18줄을 복붙해 재구현했는데, 정작
    // PriceGapFillService.fillDomesticGap이 부르는 일별 갭필 경로
    // (refreshRecent)는 이 재시도를 안 타 국내만 429 후 재시도 없이
    // 즉시 실패가 반복되는 실제 버그가 있었다 - "이 엔드포인트를 안전하게
    // 부르는 방법"이라는 같은 책임이므로 클라이언트 레벨로 옮겨 모든
    // 호출부가 예외 없이 혜택을 받게 한다).
    //
    // 2026-08-10: 실측 결과 재시도 1회로는 부족한 경우가 있어(히트율
    // 약 10%, 실패 시 결국 재요청으로 다 채워지긴 함) 재시도 상한을
    // 완화하고, 대기시간도 고정값 대신 서버가 응답 헤더로 알려주는 값
    // (resolveRateLimitBackoffMillis 참고)을 우선 쓰도록 바꿨다 - 이
    // 상수는 이제 헤더가 없거나 파싱에 실패했을 때의 폴백값이다.
    private static final long RATE_LIMIT_BACKOFF_MS = 3000;
    // 헤더 값이 비정상적으로 작거나(0에 가까워 즉시 재요청) 크면(절대
    // 타임스탬프를 초 단위 델타로 오인하는 등) 안전한 범위로 강제한다.
    private static final long MIN_RATE_LIMIT_BACKOFF_MS = 500;
    private static final long MAX_RATE_LIMIT_BACKOFF_MS = 10_000;
    // 최초 시도 포함 총 시도 횟수는 이 값 + 1(예: 2면 최대 3회 시도).
    private static final int MAX_RATE_LIMIT_RETRIES = 2;
    // 토스 API 스펙에 정확한 의미(델타 초 vs 절대 타임스탬프)가 확정돼
    // 있지 않은 헤더라 Retry-After보다 후순위 폴백으로만 쓴다(CLAUDE.md
    // §10 참고).
    private static final String HEADER_X_RATE_LIMIT_RESET = "X-RateLimit-Reset";

    private final RestClient tossRestClient;
    private final TossTokenManager tokenManager;
    private final MeterRegistry meterRegistry;

    private final Object candleRateLimitLock = new Object();
    private volatile long lastCandleCallAtMillis = 0;

    @CircuitBreaker(name = "toss")
    @Bulkhead(name = "toss")
    public TossCandleResponse getDailyCandles(String symbol, int count, String before) {
        for (int attempt = 0; ; attempt++) {
            try {
                return fetchDailyCandles(symbol, count, before);
            } catch (ExternalApiException e) {
                boolean rateLimited = TossApiErrorCode.RATE_LIMIT_EXCEEDED.getCode().equals(e.getCode());
                if (!rateLimited || attempt >= MAX_RATE_LIMIT_RETRIES) {
                    throw e;
                }
                long backoffMs = resolveRateLimitBackoffMillis(e);
                log.warn("캔들 조회 Rate Limit 도달({}/{}), {}ms 대기 후 재시도: symbol={}",
                    attempt + 1, MAX_RATE_LIMIT_RETRIES, backoffMs, symbol);
                if (!SleepUtil.sleepMillis(backoffMs)) {
                    throw new ExternalApiException(TossApiErrorCode.RATE_LIMIT_EXCEEDED, e);
                }
            }
        }
    }

    /**
     * 429 응답의 Retry-After(우선)/X-RateLimit-Reset(폴백) 헤더에서 대기시간을
     * 읽는다 - 둘 다 없거나 파싱에 실패하면 고정값({@link #RATE_LIMIT_BACKOFF_MS})으로
     * 폴백한다. HTTP-date 형식의 Retry-After는 이 API에서 쓰는 걸 확인한 바 없어
     * 지원하지 않는다(초 단위 정수만 파싱).
     */
    private long resolveRateLimitBackoffMillis(ExternalApiException e) {
        if (e.getCause() instanceof HttpClientErrorException httpError) {
            HttpHeaders headers = httpError.getResponseHeaders();
            if (headers != null) {
                Long seconds = parseSecondsHeader(headers.getFirst(HttpHeaders.RETRY_AFTER));
                if (seconds == null) {
                    seconds = parseSecondsHeader(headers.getFirst(HEADER_X_RATE_LIMIT_RESET));
                }
                if (seconds != null) {
                    return clampBackoffMillis(seconds * 1000);
                }
            }
        }
        return RATE_LIMIT_BACKOFF_MS;
    }

    private Long parseSecondsHeader(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds >= 0 ? seconds : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long clampBackoffMillis(long millis) {
        return Math.min(Math.max(millis, MIN_RATE_LIMIT_BACKOFF_MS), MAX_RATE_LIMIT_BACKOFF_MS);
    }

    private TossCandleResponse fetchDailyCandles(String symbol, int count, String before) {
        awaitCandleRateLimit();
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

    @CircuitBreaker(name = "toss")
    @Bulkhead(name = "toss")
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
    @CircuitBreaker(name = "toss")
    @Bulkhead(name = "toss")
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
     * 측(DomesticMarketCalendarCache)이 하루 1회만 호출하도록 캐싱한다.
     */
    @CircuitBreaker(name = "toss")
    @Bulkhead(name = "toss")
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
     * 주식 랭킹 조회(신규, 2026-07-29 toss-openapi.json 갱신으로 확인).
     * Rate Limits Group이 시세 조회(MARKET_DATA)와 분리된 RANKING이라
     * 별도 예산을 쓴다. {@code marketCountry}는 "KR"/"US", {@code type}은
     * MARKET_TRADING_AMOUNT/MARKET_TRADING_VOLUME/TOP_GAINERS/TOP_LOSERS/
     * TOSS_SECURITIES_TRADING_AMOUNT/TOSS_SECURITIES_TRADING_VOLUME,
     * {@code duration}은 realtime/1d/1w/1mo/3mo/6mo/1y - 단
     * TOP_GAINERS/TOP_LOSERS는 realtime을 지원하지 않는다(400
     * unsupported-ranking-duration). 응답 항목의 changeRate는 소수
     * 비율(0.0125=1.25%)이라 호출 측에서 ×100 필요(TossRankingResponse
     * 참고).
     */
    @CircuitBreaker(name = "toss")
    @Bulkhead(name = "toss")
    public TossRankingResponse getRankings(String type, String marketCountry, String duration, int count) {
        return withTokenRetry("rankings", token -> ExternalApiInvoker.call(
            TossApiErrorCode.RANKING_INQUIRY_FAILED,
            () -> tossRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/rankings")
                    .queryParam("type", type)
                    .queryParam("marketCountry", marketCountry)
                    .queryParam("duration", duration)
                    .queryParam("count", count)
                    .build())
                .header("authorization", "Bearer " + token)
                .retrieve()
                .body(TossRankingResponse.class),
            HttpClientErrorException.TooManyRequests.class,
            TossApiErrorCode.RATE_LIMIT_EXCEEDED));
    }

    /**
     * 해외(미국) 장 운영 캘린더 조회. Rate Limits Group이 시세 조회
     * (MARKET_DATA)와 분리된 MARKET_INFO라 별도 예산을 쓴다 - 호출 측
     * (OverseasMarketCalendarCache)이 하루 1회만 호출하도록 캐싱한다. 국내
     * 캘린더와 응답 형태가 다르다(TossUsMarketCalendarResponse 주석 참고).
     */
    @CircuitBreaker(name = "toss")
    @Bulkhead(name = "toss")
    public TossUsMarketCalendarResponse getUsMarketCalendar() {
        return withTokenRetry("us-market-calendar", token -> ExternalApiInvoker.call(
            TossApiErrorCode.US_MARKET_CALENDAR_INQUIRY_FAILED,
            () -> tossRestClient.get()
                .uri("/api/v1/market-calendar/US")
                .header("authorization", "Bearer " + token)
                .retrieve()
                .body(TossUsMarketCalendarResponse.class)));
    }

    /**
     * 시장 지표(국내 지수·국채) 현재가 조회(신규, 2026-07-29). Rate Limits
     * Group이 시세 조회(MARKET_DATA)와 분리된 MARKET_INDICATOR라 별도
     * 예산을 쓴다. {@code symbols}는 콤마 구분(예: "KOSPI,KOSDAQ"). 응답에
     * {@code lastPrice}만 있고 등락률·장중여부는 없어 호출 측(MarketIndexCache)이
     * 직접 계산해야 한다.
     */
    @CircuitBreaker(name = "toss")
    @Bulkhead(name = "toss")
    public TossMarketIndicatorPriceResponse getMarketIndicatorPrices(String symbols) {
        return withTokenRetry("market-indicator-prices", token -> ExternalApiInvoker.call(
            TossApiErrorCode.MARKET_INDICATOR_PRICE_INQUIRY_FAILED,
            () -> tossRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/market-indicators/prices")
                    .queryParam("symbols", symbols)
                    .build())
                .header("authorization", "Bearer " + token)
                .retrieve()
                .body(TossMarketIndicatorPriceResponse.class),
            HttpClientErrorException.TooManyRequests.class,
            TossApiErrorCode.RATE_LIMIT_EXCEEDED));
    }

    /**
     * 시장 지표(국내 지수·국채) 캔들 조회(신규, 2026-07-29). {@code interval}
     * {@code 1m}(분봉)은 KOSPI/KOSDAQ만 지원, 국채는 {@code 1d}(일봉)만
     * 지원한다(호출 측이 지수 코드에 맞게 선택). Rate Limits Group은 위
     * 현재가 조회와 동일한 MARKET_INDICATOR.
     */
    @CircuitBreaker(name = "toss")
    @Bulkhead(name = "toss")
    public TossMarketIndicatorCandleResponse getMarketIndicatorCandles(
        String symbol, String interval, int count, String before) {
        return withTokenRetry("market-indicator-candles", token -> ExternalApiInvoker.call(
            TossApiErrorCode.MARKET_INDICATOR_CANDLE_INQUIRY_FAILED,
            () -> tossRestClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .path("/api/v1/market-indicators/{symbol}/candles")
                        .queryParam("interval", interval)
                        .queryParam("count", count);
                    if (before != null) {
                        builder.queryParam("before", before);
                    }
                    return builder.build(symbol);
                })
                .header("authorization", "Bearer " + token)
                .retrieve()
                .body(TossMarketIndicatorCandleResponse.class),
            HttpClientErrorException.TooManyRequests.class,
            TossApiErrorCode.RATE_LIMIT_EXCEEDED));
    }

    /**
     * 투자자별 매매대금 조회(신규, 2026-07-29) - KOSPI/KOSDAQ만 지원(그 외
     * 심볼은 400 unsupported-symbol). {@code interval}은 1d/1w/1mo/1y,
     * {@code until}은 페이지네이션 커서(이전 응답의 nextUntil을 그대로
     * 전달, 최초 호출 시 null). Rate Limits Group은 MARKET_INDICATOR로
     * 위 두 메서드와 동일.
     */
    @CircuitBreaker(name = "toss")
    @Bulkhead(name = "toss")
    public TossInvestorTradingResponse getInvestorTrading(
        String symbol, String interval, int count, String until) {
        return withTokenRetry("investor-trading", token -> ExternalApiInvoker.call(
            TossApiErrorCode.INVESTOR_TRADING_INQUIRY_FAILED,
            () -> tossRestClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .path("/api/v1/market-indicators/{symbol}/investor-trading")
                        .queryParam("interval", interval)
                        .queryParam("count", count);
                    if (until != null) {
                        builder.queryParam("until", until);
                    }
                    return builder.build(symbol);
                })
                .header("authorization", "Bearer " + token)
                .retrieve()
                .body(TossInvestorTradingResponse.class),
            HttpClientErrorException.TooManyRequests.class,
            TossApiErrorCode.RATE_LIMIT_EXCEEDED));
    }

    /**
     * getDailyCandles를 호출하는 스레드가 몇 개든 이 메서드가 결합 호출률을
     * {@value #MIN_CANDLE_CALL_INTERVAL_MS}ms당 1회로 강제한다 - 락을 쥔 채
     * sleep해 대기 중인 다른 스레드가 동시에 통과하지 못하게 한다(그래야
     * "직전 호출 이후 최소 간격"이라는 불변식이 스레드 수와 무관하게 유지됨).
     */
    private void awaitCandleRateLimit() {
        synchronized (candleRateLimitLock) {
            long waitMs = lastCandleCallAtMillis + MIN_CANDLE_CALL_INTERVAL_MS - System.currentTimeMillis();
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastCandleCallAtMillis = System.currentTimeMillis();
        }
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
