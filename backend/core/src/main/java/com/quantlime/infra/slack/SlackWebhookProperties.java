package com.quantlime.infra.slack;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "slack")
public class SlackWebhookProperties {

    private final String feedbackWebhookUrl;
}
