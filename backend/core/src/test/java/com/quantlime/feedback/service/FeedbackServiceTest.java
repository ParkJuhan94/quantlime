package com.quantlime.feedback.service;

import static org.mockito.Mockito.verify;

import com.quantlime.feedback.dto.request.SendFeedbackRequest;
import com.quantlime.feedback.implement.FeedbackNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackNotifier feedbackNotifier;

    @InjectMocks
    private FeedbackService feedbackService;

    @Test
    @DisplayName("[의견을 알림 구현체로 위임한다]")
    void sendFeedback_delegatesToNotifier() {
        SendFeedbackRequest request = new SendFeedbackRequest("BUG", "차트가 깨져요", null, null);

        feedbackService.sendFeedback(request);

        verify(feedbackNotifier).notify(request);
    }
}
