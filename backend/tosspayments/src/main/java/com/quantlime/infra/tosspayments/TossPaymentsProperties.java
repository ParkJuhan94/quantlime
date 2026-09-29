package com.quantlime.infra.tosspayments;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "toss-payments")
public class TossPaymentsProperties {

    private final String clientKey;
    private final String secretKey;
    private final String webhookSecret;
    private final String baseUrl;
}
