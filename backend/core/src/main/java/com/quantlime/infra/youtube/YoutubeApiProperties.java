package com.quantlime.infra.youtube;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "youtube")
public class YoutubeApiProperties {

    private final String apiKey;
    private final String baseUrl;
}
