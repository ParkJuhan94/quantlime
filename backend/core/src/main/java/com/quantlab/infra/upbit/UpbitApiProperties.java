package com.quantlab.infra.upbit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "upbit")
public class UpbitApiProperties {

    private final String baseUrl;
}
