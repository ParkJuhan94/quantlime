package com.quantlab.infra.kind;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "kind")
public class KindApiProperties {

    private final String baseUrl;
}
