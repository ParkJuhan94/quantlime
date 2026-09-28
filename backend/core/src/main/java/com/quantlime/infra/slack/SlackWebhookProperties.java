package com.quantlime.infra.slack;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "slack")
public class SlackWebhookProperties {

    private final String feedbackWebhookUrl;
    // 피드백 채널과 분리(운영 알림 오폭 시 사용자 피드백 채널까지 오염되는 걸 방지) -
    // 채널 분리 원칙은 monitoring/alertmanager 쪽과 동일.
    private final String opsWebhookUrl;
}
