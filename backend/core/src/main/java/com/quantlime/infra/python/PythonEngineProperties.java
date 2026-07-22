package com.quantlime.infra.python;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "python-engine")
public class PythonEngineProperties {

    private final String baseUrl;
}
