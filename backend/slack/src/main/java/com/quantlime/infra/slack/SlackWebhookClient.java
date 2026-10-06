package com.quantlime.infra.slack;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.infra.slack.exception.SlackApiErrorCode;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
 *
 * <p>진입점마다 {@code @CircuitBreaker}/{@code @Bulkhead}("slack" 인스턴스)
 * 적용(2026-09-24) - 피드백 채널과 운영 알림 채널이 URL만 다를 뿐 같은 Slack
 * 인프라라 인스턴스를 나누지 않았다. 실제 시스템 장애로 DLT 알림이 몰리는
 * 와중에 Slack 자체가 느려지면, 그 알림 호출들이 컨슈머 스레드를 붙잡고
 * 늘어지는 걸 막는 게 이 서킷브레이커의 존재 이유다.
 */
@Component
@RequiredArgsConstructor
public class SlackWebhookClient {

    private final SlackWebhookProperties properties;
    private final RestClient slackRestClient;

    @CircuitBreaker(name = "slack", fallbackMethod = "sendFallback")
    @Bulkhead(name = "slack")
    public void sendMessage(String text) {
        send(properties.getFeedbackWebhookUrl(), text);
    }

    /** 운영 알림(DLT 등) 전용 - 사용자 피드백 채널과 분리된 별도 웹훅으로 보낸다. */
    @CircuitBreaker(name = "slack", fallbackMethod = "sendFallback")
    @Bulkhead(name = "slack")
    public void sendOpsMessage(String text) {
        send(properties.getOpsWebhookUrl(), text);
    }

    // 서킷 open 시 resilience4j가 private send()에 도달하기도 전에 곧장
    // CallNotPermittedException을 던진다. 이 폴백이 없으면 그 예외가 그대로
    // 호출측(FeedbackService 등)까지 전파돼 GlobalExceptionHandler의
    // catch-all(500)로 새고 만다 - ExternalApiException으로 통일해 기존
    // 503 응답 경로를 그대로 타게 한다.
    @SuppressWarnings("unused")
    private void sendFallback(String text, Throwable t) {
        if (t instanceof ExternalApiException e) {
            throw e;
        }
        throw new ExternalApiException(SlackApiErrorCode.WEBHOOK_SEND_FAILED, t);
    }

    private void send(String webhookUrl, String text) {
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
