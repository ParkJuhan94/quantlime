package com.quantlime.feedback.service;

import com.quantlime.common.upload.FileStorageProperties;
import com.quantlime.feedback.dto.request.SendFeedbackRequest;
import com.quantlime.infra.slack.SlackWebhookClient;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SlackWebhookClient slackWebhookClient;
    private final FileStorageProperties fileStorageProperties;

    // Incoming Webhook은 파일 업로드 자체를 지원하지 않지만(SlackWebhookClient
    // 참고), 이미지를 먼저 /api/uploads/images로 올려 절대 URL 링크로
    // 메시지에 넣으면 Slack이 자동으로 미리보기를 언퍼널링해준다(2026-07-17,
    // 파일 업로드 인프라 도입으로 가능해짐 - 이전엔 텍스트만 지원).
    public void sendFeedback(SendFeedbackRequest request) {
        slackWebhookClient.sendMessage(formatMessage(request));
    }

    private String formatMessage(SendFeedbackRequest request) {
        StringBuilder text = new StringBuilder();
        text.append(":speech_balloon: *새 의견 접수 [").append(categoryLabel(request.category())).append("]*\n");
        text.append(request.message()).append('\n');
        if (StringUtils.hasText(request.imageUrl())) {
            text.append("첨부 이미지: ").append(toAbsoluteUrl(request.imageUrl())).append('\n');
        }
        if (StringUtils.hasText(request.pageUrl())) {
            text.append("페이지: ").append(request.pageUrl()).append('\n');
        }
        text.append("접수 시각: ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT));
        return text.toString();
    }

    private String toAbsoluteUrl(String relativeUrl) {
        return fileStorageProperties.getPublicBaseUrl() + relativeUrl;
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "BUG" -> "버그 제보";
            case "FEATURE" -> "기능 개선";
            default -> "기타";
        };
    }
}
