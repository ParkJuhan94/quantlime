package com.quantlime.feedback.service;

import com.quantlime.feedback.dto.request.SendFeedbackRequest;
import com.quantlime.feedback.implement.FeedbackNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackNotifier feedbackNotifier;

    public void sendFeedback(SendFeedbackRequest request) {
        feedbackNotifier.notify(request);
    }
}
