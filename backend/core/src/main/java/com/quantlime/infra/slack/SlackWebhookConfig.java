package com.quantlime.infra.slack;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SlackWebhookProperties.class)
public class SlackWebhookConfig {
}
