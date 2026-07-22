package com.quantlime.feedback.service;

import com.quantlime.common.upload.FileStorageProperties;
import com.quantlime.feedback.dto.request.SendFeedbackRequest;
import com.quantlime.infra.slack.SlackWebhookClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private SlackWebhookClient slackWebhookClient;

    @Mock
    private FileStorageProperties fileStorageProperties;

    @InjectMocks
    private FeedbackService feedbackService;

    @Test
    @DisplayName("[카테고리 라벨·내용·페이지 URL을 포함한 메시지를 Slack으로 전송한다]")
    void sendFeedback_formatsMessageWithCategoryAndPageUrl() {
        // given
        SendFeedbackRequest request = new SendFeedbackRequest("BUG", "차트가 깨져요", "/stocks/005930", null);

        // when
        feedbackService.sendFeedback(request);

        // then
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(slackWebhookClient).sendMessage(captor.capture());
        String message = captor.getValue();
        assertThat(message).contains("버그 제보");
        assertThat(message).contains("차트가 깨져요");
        assertThat(message).contains("/stocks/005930");
    }

    @Test
    @DisplayName("[페이지 URL이 없으면 그 줄을 생략한다]")
    void sendFeedback_withoutPageUrl_omitsPageLine() {
        // given
        SendFeedbackRequest request = new SendFeedbackRequest("FEATURE", "다크모드 있으면 좋겠어요", null, null);

        // when
        feedbackService.sendFeedback(request);

        // then
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(slackWebhookClient).sendMessage(captor.capture());
        assertThat(captor.getValue()).doesNotContain("페이지:");
        assertThat(captor.getValue()).contains("기능 개선");
    }

    @Test
    @DisplayName("[이미지가 첨부되면 절대 URL 링크를 메시지에 포함한다]")
    void sendFeedback_withImage_includesAbsoluteImageUrl() {
        // given
        given(fileStorageProperties.getPublicBaseUrl()).willReturn("http://localhost:8080");
        SendFeedbackRequest request = new SendFeedbackRequest("BUG", "이거 보세요", null, "/uploads/abc.png");

        // when
        feedbackService.sendFeedback(request);

        // then
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(slackWebhookClient).sendMessage(captor.capture());
        assertThat(captor.getValue()).contains("첨부 이미지: http://localhost:8080/uploads/abc.png");
    }
}
