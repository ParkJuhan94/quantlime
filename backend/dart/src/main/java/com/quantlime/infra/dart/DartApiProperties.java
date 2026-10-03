package com.quantlime.infra.dart;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "dart")
public class DartApiProperties {

    private final String apiKey;
    private final String baseUrl;
}
