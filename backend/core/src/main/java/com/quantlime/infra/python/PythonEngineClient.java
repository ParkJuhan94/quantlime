package com.quantlime.infra.python;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.common.util.SleepUtil;
import com.quantlime.infra.python.dto.BacktestApiRequest;
import com.quantlime.infra.python.dto.BacktestApiResponse;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiRequest;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiResponse;
import com.quantlime.infra.python.dto.ScoreBatchApiRequest;
import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse;
import com.quantlime.infra.python.dto.SummarizeApiRequest;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.infra.python.dto.TranscribeApiRequest;
import com.quantlime.infra.python.dto.TranscribeApiResponse;
import com.quantlime.infra.python.exception.PythonEngineErrorCode;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 진입점(public) 메서드마다 {@code @CircuitBreaker}/{@code @Bulkhead}(둘 다
 * {@code "quant-engine"} 인스턴스, application.yml 설정 참고)를 붙인다. 별도
 * fallback 메서드는 두지 않는다 - 예외 전파가 곧 "이번 재계산/요약을
 * 건너뛴다"는 뜻이고, 스코어는 DB에 남은 직전 값이 그대로 서빙되는 구조가
 * 이미 fallback 역할을 한다(ScoreService 클래스 주석, CLAUDE.md §10 참고,
 * 2026-08-17). {@link #summarize}가 텔레그램 다이제스트 재생성 중(재시도까지
 * 소진 후) 던지는 예외도 동일 정책을 따른다 -
 * {@code TelegramDigestGenerationFacade.generateForChannel}이 이 예외를 잡고
 * {@code TelegramDigestPersistService.persistResult}를 아예 호출하지 않으므로,
 * 그날 갱신은 실패해도 직전 다이제스트(어제/오전 값)가 그대로 서빙된다
 * (2026-08-19 확인 - Gemini 무료 티어 쿼터가 유튜브와 21/20으로 근접해 있어
 * 실제로 걸릴 수 있는 경로).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonEngineClient {

    // 퀀트 엔진 장애 시 CLAUDE.md §10 정책대로 "직전 스코어를 그대로 반환"하는
    // fallback으로 넘어가므로, 장애가 실제로 얼마나 자주 나는지가 SLO의 핵심
    // 관측 지표다.
    private static final String METRIC_CALLS = "python-engine.calls";
    private static final String METRIC_DURATION = "python-engine.duration";
    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_FAILURE = "failure";

    // Gemini 무료 티어(gemini-3.5-flash-lite)의 분당 요청 한도(RPM=15)에 걸리면
    // Gemini가 응답에 담아주는 재시도 대기시간이 대략 50~60초였다(2026-08-09,
    // 신규 채널 백로그 재처리 중 실측) - Toss처럼 3초 정도의 짧은 백오프로는
    // 분당 쿼터가 회복되지 않아 1분 정도 대기 후 1회만 재시도한다. 그래도
    // 실패하면 SummaryCollectionFacade가 FAILED로 기록해 다음 배치(최대
    // 3회 재시도)에서 자연히 다시 시도된다.
    private static final long SUMMARY_RATE_LIMIT_BACKOFF_MS = 60_000;

    private final RestClient pythonEngineRestClient;
    private final MeterRegistry meterRegistry;

    @CircuitBreaker(name = "quant-engine")
    @Bulkhead(name = "quant-engine")
    public ScoreSeriesBatchApiResponse calculateScoreSeries(ScoreBatchApiRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            ScoreSeriesBatchApiResponse response = ExternalApiInvoker.call(
                PythonEngineErrorCode.SCORE_CALCULATION_FAILED, () ->
                    pythonEngineRestClient.post()
                        .uri("/calculate/score/series")
                        .body(request)
                        .retrieve()
                        .body(ScoreSeriesBatchApiResponse.class));
            recordOutcome(OUTCOME_SUCCESS, sample);
            return response;
        } catch (ExternalApiException e) {
            recordOutcome(OUTCOME_FAILURE, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "quant-engine")
    @Bulkhead(name = "quant-engine")
    public BacktestApiResponse runBacktest(BacktestApiRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            BacktestApiResponse response = ExternalApiInvoker.call(
                PythonEngineErrorCode.BACKTEST_CALCULATION_FAILED, () ->
                    pythonEngineRestClient.post()
                        .uri("/backtest/score")
                        .body(request)
                        .retrieve()
                        .body(BacktestApiResponse.class));
            recordOutcome(OUTCOME_SUCCESS, sample);
            return response;
        } catch (ExternalApiException e) {
            recordOutcome(OUTCOME_FAILURE, sample);
            throw e;
        }
    }

    // 널 테스트(nullTest=true)는 시장 하나당 반복 200회 순환이동 재계산을
    // 포함해 다른 엔진 호출보다 훨씬 오래 걸릴 수 있어(로컬 실측 수 초~수십
    // 초, application.yml의 quant-engine 인스턴스 read timeout이 이 호출도
    // 함께 적용됨을 인지할 것 - 필요시 별도 타임아웃 인스턴스 분리는 후속
    // 검토), 운영 배치가 아니라 dev 엔드포인트 수동 트리거로만 호출한다
    // (DevController 참고).
    @CircuitBreaker(name = "quant-engine")
    @Bulkhead(name = "quant-engine")
    public CrossSectionalBacktestApiResponse runCrossSectionalBacktest(CrossSectionalBacktestApiRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            CrossSectionalBacktestApiResponse response = ExternalApiInvoker.call(
                PythonEngineErrorCode.CROSS_SECTIONAL_BACKTEST_FAILED, () ->
                    pythonEngineRestClient.post()
                        .uri("/backtest/cross-sectional")
                        .body(request)
                        .retrieve()
                        .body(CrossSectionalBacktestApiResponse.class));
            recordOutcome(OUTCOME_SUCCESS, sample);
            return response;
        } catch (ExternalApiException e) {
            recordOutcome(OUTCOME_FAILURE, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "quant-engine")
    @Bulkhead(name = "quant-engine")
    public TranscribeApiResponse fetchTranscript(TranscribeApiRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            TranscribeApiResponse response = ExternalApiInvoker.call(
                PythonEngineErrorCode.TRANSCRIPT_FETCH_FAILED, () ->
                    pythonEngineRestClient.post()
                        .uri("/transcribe")
                        .body(request)
                        .retrieve()
                        .body(TranscribeApiResponse.class));
            recordOutcome(OUTCOME_SUCCESS, sample);
            return response;
        } catch (ExternalApiException e) {
            recordOutcome(OUTCOME_FAILURE, sample);
            throw e;
        }
    }

    @CircuitBreaker(name = "quant-engine")
    @Bulkhead(name = "quant-engine")
    public SummarizeApiResponse summarize(SummarizeApiRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            SummarizeApiResponse response = summarizeWithRateLimitRetry(request);
            recordOutcome(OUTCOME_SUCCESS, sample);
            return response;
        } catch (ExternalApiException e) {
            recordOutcome(OUTCOME_FAILURE, sample);
            throw e;
        }
    }

    // TossApiClient.getDailyCandles와 동일한 "1회 재시도" 패턴 - fetchSummarize가
    // HttpClientErrorException.TooManyRequests(429)만 SUMMARY_RATE_LIMIT_EXCEEDED로
    // 구분해서 던지므로, 그 외 실패(SUMMARY_GENERATION_FAILED)는 그대로 올려보낸다.
    private SummarizeApiResponse summarizeWithRateLimitRetry(SummarizeApiRequest request) {
        try {
            return fetchSummarize(request);
        } catch (ExternalApiException e) {
            if (!PythonEngineErrorCode.SUMMARY_RATE_LIMIT_EXCEEDED.getCode().equals(e.getCode())) {
                throw e;
            }
            log.warn("AI 요약 생성 Rate Limit 도달, {}ms 대기 후 1회 재시도: videoTitle={}",
                SUMMARY_RATE_LIMIT_BACKOFF_MS, request.videoTitle());
            if (!SleepUtil.sleepMillis(SUMMARY_RATE_LIMIT_BACKOFF_MS)) {
                throw e;
            }
            return fetchSummarize(request);
        }
    }

    private SummarizeApiResponse fetchSummarize(SummarizeApiRequest request) {
        return ExternalApiInvoker.call(
            PythonEngineErrorCode.SUMMARY_GENERATION_FAILED,
            () -> pythonEngineRestClient.post()
                .uri("/summarize")
                .body(request)
                .retrieve()
                .body(SummarizeApiResponse.class),
            HttpClientErrorException.TooManyRequests.class,
            PythonEngineErrorCode.SUMMARY_RATE_LIMIT_EXCEEDED);
    }

    private void recordOutcome(String outcome, Timer.Sample sample) {
        meterRegistry.counter(METRIC_CALLS, "outcome", outcome).increment();
        sample.stop(meterRegistry.timer(METRIC_DURATION, "outcome", outcome));
    }
}
