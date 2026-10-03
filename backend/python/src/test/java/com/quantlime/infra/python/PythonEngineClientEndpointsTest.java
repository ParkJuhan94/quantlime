package com.quantlime.infra.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.python.dto.BacktestApiRequest;
import com.quantlime.infra.python.dto.BacktestApiResponse;
import com.quantlime.infra.python.dto.CrossSectionNormalizeApiRequest;
import com.quantlime.infra.python.dto.CrossSectionNormalizeApiResponse;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiRequest;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiResponse;
import com.quantlime.infra.python.dto.ScoreBatchApiRequest;
import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse;
import com.quantlime.infra.python.dto.TranscribeApiRequest;
import com.quantlime.infra.python.dto.TranscribeApiResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * {@code PythonEngineClientTest}가 summarize(429 재시도·일일 쿼터)를 다루므로, 여기서는 나머지 5개
 * 엔드포인트의 요청 계약(경로·snake_case 본문)과 응답 매핑, 실패 시 도메인 에러코드·메트릭을 검증한다.
 * 이 호출들의 예외 전파가 곧 "직전 스코어를 서빙한다"는 폴백 정책의 전제다(클래스 주석 참고).
 */
@Tag("unit")
class PythonEngineClientEndpointsTest {

    private static final String BASE_URL = "https://python-engine.test";

    private MockRestServiceServer mockServer;
    private SimpleMeterRegistry meterRegistry;
    private PythonEngineClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        meterRegistry = new SimpleMeterRegistry();
        client = new PythonEngineClient(builder.build(), meterRegistry,
            Mockito.mock(GeminiDailyQuotaGate.class));
    }

    private double calls(String outcome) {
        return meterRegistry.counter("python-engine.calls", "outcome", outcome).count();
    }

    @Test
    @DisplayName("[스코어 시계열은 /calculate/score/series로 snake_case 본문을 보내고 응답을 매핑한다]")
    void calculateScoreSeries_postsAndMaps() {
        mockServer.expect(requestTo(BASE_URL + "/calculate/score/series"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.stocks[0].stock_code").value("005930"))
            .andExpect(jsonPath("$.stocks[0].ohlcv[0].close").value(105.0))
            .andRespond(withSuccess("""
                {"scores":[{"stock_code":"005930","daily_scores":[{"date":"2026-09-30","trend_score":61.5,
                "mean_reversion_score":40.0,"composite_score":55.0,"grade":"BUY","quadrant":"상승추세 눌림목",
                "insufficient_data":false}]}]}
                """, MediaType.APPLICATION_JSON));

        ScoreSeriesBatchApiResponse response = client.calculateScoreSeries(new ScoreBatchApiRequest(List.of(
            new ScoreBatchApiRequest.StockScoreApiRequest("005930",
                List.of(new ScoreBatchApiRequest.OhlcvApiItem("2026-09-30", 100, 110, 95, 105, 1000))))));

        assertThat(response.scores()).hasSize(1);
        assertThat(response.scores().get(0).stockCode()).isEqualTo("005930");
        assertThat(response.scores().get(0).dailyScores().get(0).trendScore()).isEqualTo(61.5);
        assertThat(response.scores().get(0).dailyScores().get(0).grade()).isEqualTo("BUY");
        assertThat(calls("success")).isEqualTo(1.0);
        assertThat(calls("failure")).isZero();
        mockServer.verify();
    }

    @Test
    @DisplayName("[단일 종목 백테스트는 /backtest/score로 벤치마크까지 보내고 응답을 매핑한다]")
    void runBacktest_postsAndMaps() {
        mockServer.expect(requestTo(BASE_URL + "/backtest/score"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.stock_code").value("005930"))
            .andExpect(jsonPath("$.benchmark_ohlcv[0].date").value("2026-09-30"))
            .andRespond(withSuccess("""
                {"stock_code":"005930","score_version":"v3.0","sample_days":250,"axes":[],"daily_scores":[]}
                """, MediaType.APPLICATION_JSON));
        BacktestApiRequest.OhlcvApiItem bar = new BacktestApiRequest.OhlcvApiItem("2026-09-30", 1, 2, 0.5, 1.5, 10);

        BacktestApiResponse response = client.runBacktest(new BacktestApiRequest("005930", List.of(bar), List.of(bar)));

        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.scoreVersion()).isEqualTo("v3.0");
        assertThat(response.sampleDays()).isEqualTo(250);
        assertThat(calls("success")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[횡단면 백테스트는 /backtest/cross-sectional로 null_test/null_repeats를 포함해 보낸다]")
    void runCrossSectionalBacktest_postsAndMaps() {
        mockServer.expect(requestTo(BASE_URL + "/backtest/cross-sectional"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.market").value("KOSPI"))
            .andExpect(jsonPath("$.score_version").value("v3.0"))
            .andExpect(jsonPath("$.null_test").value(true))
            .andExpect(jsonPath("$.null_repeats").value(50))
            .andRespond(withSuccess("""
                {"market":"KOSPI","score_version":"v3.0","stock_count":300,"axis":"trend","horizon":20,
                "mean_ic":0.05,"n_dates":200,"n_observations":60000,"buckets":[]}
                """, MediaType.APPLICATION_JSON));

        CrossSectionalBacktestApiResponse response = client.runCrossSectionalBacktest(
            new CrossSectionalBacktestApiRequest("KOSPI", "v3.0", List.of(), List.of(), "trend", 20, true, 50));

        assertThat(response.market()).isEqualTo("KOSPI");
        assertThat(response.stockCount()).isEqualTo(300);
        assertThat(response.meanIc()).isEqualTo(0.05);
        assertThat(calls("success")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[횡단면 정규화는 /normalize/cross-section으로 as_of·peer_group을 보내고 백분위를 매핑한다]")
    void normalizeCrossSection_postsAndMaps() {
        mockServer.expect(requestTo(BASE_URL + "/normalize/cross-section"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.as_of").value("2026-09-30"))
            .andExpect(jsonPath("$.peer_group").value("domestic"))
            .andRespond(withSuccess("""
                {"as_of":"2026-09-30","peer_group":"domestic","min_sample_met":true,
                "items":[{"stock_code":"005930","trend_percentile":90.0,"mean_reversion_percentile":10.0,
                "composite_percentile":75.0}]}
                """, MediaType.APPLICATION_JSON));

        CrossSectionNormalizeApiResponse response = client.normalizeCrossSection(
            new CrossSectionNormalizeApiRequest("2026-09-30", "domestic", List.of(
                new CrossSectionNormalizeApiRequest.AxisScoreApiItem("005930", 60.0, 40.0))));

        assertThat(response.minSampleMet()).isTrue();
        assertThat(response.items().get(0).compositePercentile()).isEqualTo(75.0);
        assertThat(calls("success")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[자막 조회는 /transcribe로 video_id를 보내고 자막이 없으면 available=false와 사유를 그대로 받는다]")
    void fetchTranscript_postsAndMaps_includingUnavailable() {
        mockServer.expect(requestTo(BASE_URL + "/transcribe"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.video_id").value("vid-1"))
            .andRespond(withSuccess("""
                {"available":false,"source":null,"lang":null,"content":null,"char_count":null,"reason":"자막 없음"}
                """, MediaType.APPLICATION_JSON));

        TranscribeApiResponse response = client.fetchTranscript(new TranscribeApiRequest("vid-1"));

        assertThat(response.available()).isFalse();
        assertThat(response.reason()).isEqualTo("자막 없음");
        assertThat(calls("success")).isEqualTo(1.0);
    }

    static Stream<Arguments> failingCalls() {
        BacktestApiRequest.OhlcvApiItem bar = new BacktestApiRequest.OhlcvApiItem("2026-09-30", 1, 2, 0.5, 1.5, 10);
        return Stream.of(
            Arguments.of("스코어 시계열", "/calculate/score/series", "PYE_000",
                (Consumer<PythonEngineClient>) c -> c.calculateScoreSeries(new ScoreBatchApiRequest(List.of()))),
            Arguments.of("백테스트", "/backtest/score", "PYE_001",
                (Consumer<PythonEngineClient>) c -> c.runBacktest(new BacktestApiRequest("A", List.of(bar), List.of(bar)))),
            Arguments.of("횡단면 백테스트", "/backtest/cross-sectional", "PYE_005",
                (Consumer<PythonEngineClient>) c -> c.runCrossSectionalBacktest(
                    new CrossSectionalBacktestApiRequest("KOSPI", "v3.0", List.of(), List.of(), "trend", 5, false, 0))),
            Arguments.of("횡단면 정규화", "/normalize/cross-section", "PYE_007",
                (Consumer<PythonEngineClient>) c -> c.normalizeCrossSection(
                    new CrossSectionNormalizeApiRequest("2026-09-30", "domestic", List.of()))),
            Arguments.of("자막 조회", "/transcribe", "PYE_002",
                (Consumer<PythonEngineClient>) c -> c.fetchTranscript(new TranscribeApiRequest("vid-1"))));
    }

    @ParameterizedTest(name = "[{0}] 엔진 5xx는 도메인 에러코드 {2}로 감싸고 실패 메트릭만 올린다]")
    @MethodSource("failingCalls")
    void engineServerError_wrapsWithDomainCode_andCountsFailure(
        String name, String uri, String expectedCode, Consumer<PythonEngineClient> call) {
        mockServer.expect(requestTo(BASE_URL + uri)).andRespond(withServerError());

        assertThatThrownBy(() -> call.accept(client))
            .isInstanceOfSatisfying(ExternalApiException.class, e -> assertThat(e.getCode()).isEqualTo(expectedCode));
        assertThat(calls("failure")).isEqualTo(1.0);
        assertThat(calls("success")).isZero();
        assertThat(meterRegistry.timer("python-engine.duration", "outcome", "failure").count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("[응답 본문이 비어 있으면(null) 같은 도메인 에러코드로 실패한다]")
    void emptyBody_failsWithDomainCode() {
        mockServer.expect(requestTo(BASE_URL + "/transcribe")).andRespond(withSuccess());

        assertThatThrownBy(() -> client.fetchTranscript(new TranscribeApiRequest("vid-1")))
            .isInstanceOfSatisfying(ExternalApiException.class, e -> assertThat(e.getCode()).isEqualTo("PYE_002"));
        assertThat(calls("failure")).isEqualTo(1.0);
    }
}
