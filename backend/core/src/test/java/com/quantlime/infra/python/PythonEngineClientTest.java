package com.quantlime.infra.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.util.SleepUtil;
import com.quantlime.infra.python.dto.SummarizeApiRequest;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.infra.python.exception.PythonEngineErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@Tag("unit")
class PythonEngineClientTest {

    private static final String BASE_URL = "https://python-engine.test";
    private static final String SUMMARIZE_URI = BASE_URL + "/summarize";
    private static final String SUCCESS_BODY = "{\"summary\":\"요약\",\"key_points\":[],\"macro_points\":[],"
        + "\"mentioned_tickers\":[],\"caveat\":\"고지\",\"model\":\"gemini-3.5-flash-lite\","
        + "\"input_tokens\":100,\"output_tokens\":50}";

    private MockRestServiceServer mockServer;
    private PythonEngineClient pythonEngineClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        pythonEngineClient = new PythonEngineClient(builder.build(), new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("[summarize는 429(Rate Limit) 발생 시 대기 후 1회 재시도한다 - "
        + "TossApiClient.getDailyCandles와 동일 패턴. 실제 대기(60초)는 테스트에서 SleepUtil을 정적 모킹해 생략한다]")
    void summarize_rateLimited_retriesOnce() {
        // given
        mockServer.expect(requestTo(SUMMARIZE_URI))
            .andExpect(method(POST))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":\"Gemini API rate limit exceeded\"}"));
        mockServer.expect(requestTo(SUMMARIZE_URI))
            .andExpect(method(POST))
            .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        try (MockedStatic<SleepUtil> sleepUtil = Mockito.mockStatic(SleepUtil.class)) {
            sleepUtil.when(() -> SleepUtil.sleepMillis(60_000L)).thenReturn(true);

            // when
            SummarizeApiResponse response = pythonEngineClient.summarize(
                new SummarizeApiRequest("제목", "채널", "자막"));

            // then
            assertThat(response.summary()).isEqualTo("요약");
            mockServer.verify();
        }
    }

    @Test
    @DisplayName("[summarize는 429가 재시도에서도 반복되면 SUMMARY_RATE_LIMIT_EXCEEDED로 전파한다]")
    void summarize_rateLimitedTwice_propagatesRateLimitError() {
        // given
        mockServer.expect(requestTo(SUMMARIZE_URI))
            .andExpect(method(POST))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":\"Gemini API rate limit exceeded\"}"));
        mockServer.expect(requestTo(SUMMARIZE_URI))
            .andExpect(method(POST))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":\"Gemini API rate limit exceeded\"}"));

        try (MockedStatic<SleepUtil> sleepUtil = Mockito.mockStatic(SleepUtil.class)) {
            sleepUtil.when(() -> SleepUtil.sleepMillis(60_000L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> pythonEngineClient.summarize(new SummarizeApiRequest("제목", "채널", "자막")))
                .isInstanceOf(ExternalApiException.class)
                .hasFieldOrPropertyWithValue("code", PythonEngineErrorCode.SUMMARY_RATE_LIMIT_EXCEEDED.getCode());
            mockServer.verify();
        }
    }

    @Test
    @DisplayName("[summarize는 429가 아닌 실패는 재시도 없이 SUMMARY_GENERATION_FAILED로 전파한다]")
    void summarize_nonRateLimitFailure_doesNotRetry() {
        // given
        mockServer.expect(requestTo(SUMMARIZE_URI))
            .andExpect(method(POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // when & then
        assertThatThrownBy(() -> pythonEngineClient.summarize(new SummarizeApiRequest("제목", "채널", "자막")))
            .isInstanceOf(ExternalApiException.class)
            .hasFieldOrPropertyWithValue("code", PythonEngineErrorCode.SUMMARY_GENERATION_FAILED.getCode());
        mockServer.verify();
    }
}
