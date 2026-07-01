package com.quantlab.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
    info = @Info(
        title = "QuantLab API",
        description = "국내 주식 기술적 지표 스코어링 & 모니터링 API",
        version = "v0.1"
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Local Server")
    }
)
@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi quantLabApi() {
        return GroupedOpenApi.builder()
            .group("QuantLab API v0.1")
            .pathsToMatch("/api/**")
            .build();
    }
}
