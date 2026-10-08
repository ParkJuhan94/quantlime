package com.quantlime.infra.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.slack.exception.SlackApiErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 서킷브레이커 애너테이션은 단위 테스트에서 동작하지 않으므로, 실제 전송 경로(피드백/운영 웹훅 분리, 실패 매핑)와
 * 폴백 메서드를 직접 호출해 검증한다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SlackWebhookClientHttpTest {

    private static final String FEEDBACK_URL = "https://hooks.test/feedback";
    private static final String OPS_URL = "https://hooks.test/ops";

    @Mock
    private SlackWebhookProperties properties;

    private MockRestServiceServer server;
    private SlackWebhookClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SlackWebhookClient(properties, builder.build());
    }

    @Test
    @DisplayName("[피드백 메시지는 피드백 웹훅으로 text 페이로드를 POST한다]")
    void sendMessage_postsToFeedbackWebhook() {
        given(properties.getFeedbackWebhookUrl()).willReturn(FEEDBACK_URL);
        server.expect(requestTo(FEEDBACK_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"text\":\"안녕\"}"))
            .andRespond(withSuccess());

        client.sendMessage("안녕");

        server.verify();
    }

    @Test
    @DisplayName("[운영 알림은 피드백 채널과 분리된 운영 웹훅으로 보낸다]")
    void sendOpsMessage_postsToOpsWebhook() {
        given(properties.getOpsWebhookUrl()).willReturn(OPS_URL);
        server.expect(requestTo(OPS_URL)).andRespond(withSuccess());

        client.sendOpsMessage("DLT 알림");

        server.verify();
    }

    @Test
    @DisplayName("[운영 웹훅 URL이 비어 있으면 설정 누락 에러를 던진다]")
    void sendOpsMessage_blankUrl_throwsNotConfigured() {
        given(properties.getOpsWebhookUrl()).willReturn(null);

        assertThatThrownBy(() -> client.sendOpsMessage("x"))
            .isInstanceOf(ExternalApiException.class)
            .satisfies(t -> assertThat(((ExternalApiException) t).getCode())
                .isEqualTo(SlackApiErrorCode.WEBHOOK_NOT_CONFIGURED.getCode()));
    }

    @Test
    @DisplayName("[Slack이 5xx를 돌려주면 WEBHOOK_SEND_FAILED로 바꾼다]")
    void sendMessage_serverError_mapsToSendFailed() {
        given(properties.getFeedbackWebhookUrl()).willReturn(FEEDBACK_URL);
        server.expect(requestTo(FEEDBACK_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.sendMessage("x"))
            .isInstanceOf(ExternalApiException.class)
            .satisfies(t -> assertThat(((ExternalApiException) t).getCode())
                .isEqualTo(SlackApiErrorCode.WEBHOOK_SEND_FAILED.getCode()));
    }

    @Test
    @DisplayName("[서킷 폴백 - ExternalApiException은 그대로, 그 외 예외는 WEBHOOK_SEND_FAILED로 감싼다]")
    void sendFallback_wrapsNonExternalApiExceptions() {
        ExternalApiException original = new ExternalApiException(SlackApiErrorCode.WEBHOOK_NOT_CONFIGURED);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(client, "sendFallback", "x", original))
            .isSameAs(original);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
            client, "sendFallback", "x", new IllegalStateException("circuit open")))
            .isInstanceOf(ExternalApiException.class)
            .satisfies(t -> assertThat(((ExternalApiException) t).getCode())
                .isEqualTo(SlackApiErrorCode.WEBHOOK_SEND_FAILED.getCode()));
    }
}
