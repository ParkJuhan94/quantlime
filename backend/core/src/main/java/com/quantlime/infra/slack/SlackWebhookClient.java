package com.quantlime.infra.slack;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.infra.slack.exception.SlackApiErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Slack Incoming Webhook 클라이언트. 웹훅은 단순 텍스트(+블록) 메시지만
 * 보낼 수 있고 파일 업로드는 지원하지 않는다 - 이미지/동영상 첨부를
 * Slack으로 실제로 전달하려면 Bot Token 기반 files.upload API나 별도
 * 오브젝트 스토리지(S3 등)가 필요한데, 이 프로젝트엔 아직 파일 스토리지가
 * 없어 이번 범위에서는 텍스트 피드백만 다룬다.
 */
@Component
@RequiredArgsConstructor
public class SlackWebhookClient {

    private final SlackWebhookProperties properties;
    private final RestClient slackRestClient = RestClient.create();

    public void sendMessage(String text) {
        String webhookUrl = properties.getFeedbackWebhookUrl();
        if (!StringUtils.hasText(webhookUrl)) {
            throw new ExternalApiException(SlackApiErrorCode.WEBHOOK_NOT_CONFIGURED);
        }
        ExternalApiInvoker.call(
            SlackApiErrorCode.WEBHOOK_SEND_FAILED,
            () -> slackRestClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SlackMessagePayload(text))
                .retrieve()
                .toBodilessEntity());
    }

    private record SlackMessagePayload(String text) {
    }
}
