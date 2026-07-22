package com.quantlime.feedback.controller;

import com.quantlime.feedback.dto.request.SendFeedbackRequest;
import com.quantlime.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "의견 보내기 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    @Operation(
        summary = "의견(버그/기능개선) 보내기",
        description = "사이드패널 의견 보내기 폼 - Slack 채널로 전송한다(로그인 불필요)"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> sendFeedback(@Valid @RequestBody SendFeedbackRequest request) {
        feedbackService.sendFeedback(request);
        return ResponseEntity.ok().build();
    }
}
