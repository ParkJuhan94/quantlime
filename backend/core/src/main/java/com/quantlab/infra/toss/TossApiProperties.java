package com.quantlab.infra.toss;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "toss")
public class TossApiProperties {

    private final String clientId;
    private final String clientSecret;
    private final String baseUrl;
}
