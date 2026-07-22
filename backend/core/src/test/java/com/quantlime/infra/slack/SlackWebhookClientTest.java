package com.quantlime.infra.slack;

import com.quantlime.common.exception.ExternalApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SlackWebhookClientTest {

    @Mock
    private SlackWebhookProperties properties;

    @InjectMocks
    private SlackWebhookClient slackWebhookClient;

    @Test
    @DisplayName("[웹훅 URL이 비어 있으면 설정 누락 에러를 던지고 네트워크 호출을 시도하지 않는다]")
    void sendMessage_blankWebhookUrl_throwsWithoutCallingNetwork() {
        // given
        given(properties.getFeedbackWebhookUrl()).willReturn("");

        // when & then
        assertThatThrownBy(() -> slackWebhookClient.sendMessage("hello"))
            .isInstanceOf(ExternalApiException.class)
            .hasMessageContaining("설정되지 않았습니다");
    }
}
