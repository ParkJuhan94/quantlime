package com.quantlime.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
    info = @Info(
        title = "QuantLime API",
        description = "국내 주식 기술적 지표 스코어링 & 모니터링 API",
        version = "v0.1"
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Local Server")
    },
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi quantlimeApi() {
        return GroupedOpenApi.builder()
            .group("QuantLime API v0.1")
            .pathsToMatch("/api/**")
            .build();
    }
}
