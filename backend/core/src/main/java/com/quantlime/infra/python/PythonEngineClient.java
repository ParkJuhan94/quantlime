package com.quantlime.infra.python;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.infra.python.dto.BacktestApiRequest;
import com.quantlime.infra.python.dto.BacktestApiResponse;
import com.quantlime.infra.python.dto.ScoreBatchApiRequest;
import com.quantlime.infra.python.dto.ScoreBatchApiResponse;
import com.quantlime.infra.python.exception.PythonEngineErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    private final RestClient pythonEngineRestClient;
    private final MeterRegistry meterRegistry;

    public ScoreBatchApiResponse calculateScoreBatch(ScoreBatchApiRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            ScoreBatchApiResponse response = ExternalApiInvoker.call(
                PythonEngineErrorCode.SCORE_CALCULATION_FAILED, () ->
                    pythonEngineRestClient.post()
                        .uri("/calculate/score/batch")
                        .body(request)
                        .retrieve()
                        .body(ScoreBatchApiResponse.class));
            recordOutcome(OUTCOME_SUCCESS, sample);
            return response;
        } catch (ExternalApiException e) {
            recordOutcome(OUTCOME_FAILURE, sample);
            throw e;
        }
    }

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

    private void recordOutcome(String outcome, Timer.Sample sample) {
        meterRegistry.counter(METRIC_CALLS, "outcome", outcome).increment();
        sample.stop(meterRegistry.timer(METRIC_DURATION, "outcome", outcome));
    }
}
