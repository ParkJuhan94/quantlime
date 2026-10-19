package com.quantlime.common.config;

import com.quantlime.auth.resolver.LoginUser;
import com.quantlime.auth.resolver.OptionalLoginUser;
import com.quantlime.common.exception.ErrorResponseTemplate;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.Arrays;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.web.bind.annotation.RequestMapping;

// 운영에서는 application-prod.yml의 SPRINGDOC_ENABLED(기본 false)로 문서 자체가 꺼지고, 서버 URL을 상대 경로("/")로
// 둬 어느 포트/호스트로 접속하든 "Try it out"이 그 서버로 요청한다(포트를 바꿔 띄워도 라이브 서버를 건드리지 않음).
@OpenAPIDefinition(
    info = @Info(
        title = "QuantLime API",
        description = "국내 주식 기술적 지표 스코어링 & 모니터링 API",
        version = "v0.1"
    ),
    servers = {
        @Server(url = "/", description = "현재 접속 호스트")
    }
)
@SecurityScheme(
    name = SwaggerConfig.BEARER_AUTH,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
@Configuration
public class SwaggerConfig {

    static final String BEARER_AUTH = "bearerAuth";

    private static final String ADMIN_PATH = "/api/admin/**";
    private static final String WEBHOOK_PATH = "/api/webhooks/**";
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final String ERROR_SCHEMA = "ErrorResponseTemplate";

    // 서버는 SecurityConfig에서 경로 단위로 인증을 걸기 때문에 전역 security를 두지 않고,
    // 컨트롤러 파라미터(@LoginUser/@OptionalLoginUser)를 보고 오퍼레이션마다 자물쇠를 붙인다.
    @Bean
    public GroupedOpenApi userApi(OperationCustomizer operationCustomizer, OpenApiCustomizer errorSchemaCustomizer) {
        return GroupedOpenApi.builder()
            .group("1. 사용자")
            .pathsToMatch("/api/**")
            .pathsToExclude(ADMIN_PATH, WEBHOOK_PATH)
            .addOperationCustomizer(operationCustomizer)
            .addOpenApiCustomizer(errorSchemaCustomizer)
            .build();
    }

    @Bean
    public GroupedOpenApi adminApi(OperationCustomizer operationCustomizer, OpenApiCustomizer errorSchemaCustomizer) {
        return GroupedOpenApi.builder()
            .group("2. 관리자")
            .pathsToMatch(ADMIN_PATH)
            .addOperationCustomizer(operationCustomizer)
            .addOpenApiCustomizer(errorSchemaCustomizer)
            .build();
    }

    @Bean
    public GroupedOpenApi webhookApi(OperationCustomizer operationCustomizer, OpenApiCustomizer errorSchemaCustomizer) {
        return GroupedOpenApi.builder()
            .group("3. 웹훅")
            .pathsToMatch(WEBHOOK_PATH)
            .addOperationCustomizer(operationCustomizer)
            .addOpenApiCustomizer(errorSchemaCustomizer)
            .build();
    }

    @Bean
    @Profile("dev")
    public GroupedOpenApi devApi(OperationCustomizer operationCustomizer, OpenApiCustomizer errorSchemaCustomizer) {
        return GroupedOpenApi.builder()
            .group("4. 개발용")
            .pathsToMatch("/dev/**")
            .addOperationCustomizer(operationCustomizer)
            .addOpenApiCustomizer(errorSchemaCustomizer)
            .build();
    }

    @Bean
    public OpenApiCustomizer errorSchemaCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.components(new Components());
            }
            ModelConverters.getInstance().readAll(ErrorResponseTemplate.class)
                .forEach(openApi.getComponents()::addSchemas);
        };
    }

    @Bean
    public OperationCustomizer operationCustomizer() {
        return (operation, handlerMethod) -> {
            MethodParameter[] parameters = handlerMethod.getMethodParameters();

            boolean required = Arrays.stream(parameters)
                .anyMatch(p -> p.hasParameterAnnotation(LoginUser.class));
            boolean optional = Arrays.stream(parameters)
                .anyMatch(p -> p.hasParameterAnnotation(OptionalLoginUser.class));
            boolean admin = isAdminController(handlerMethod.getBeanType());

            // 인증 사용자 ID는 리졸버가 JWT에서 채우는 값이라 요청 파라미터로 노출하지 않는다.
            if (operation.getParameters() != null) {
                for (MethodParameter parameter : parameters) {
                    if (parameter.hasParameterAnnotation(LoginUser.class)
                        || parameter.hasParameterAnnotation(OptionalLoginUser.class)) {
                        parameter.initParameterNameDiscovery(NAME_DISCOVERER);
                        String name = parameter.getParameterName();
                        operation.getParameters().removeIf(p -> p.getName().equals(name));
                    }
                }
            }

            if (required || admin || optional) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
            }

            ApiResponses responses = operation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();
                operation.setResponses(responses);
            }
            if (required || admin) {
                responses.putIfAbsent("401", errorResponse("인증 실패 (토큰 없음/만료/위조)"));
            }
            if (admin) {
                responses.putIfAbsent("403", errorResponse("관리자 권한 필요"));
            }
            responses.putIfAbsent("500", errorResponse("서버 오류"));
            return operation;
        };
    }

    private static boolean isAdminController(Class<?> beanType) {
        RequestMapping mapping = beanType.getAnnotation(RequestMapping.class);
        return mapping != null && Arrays.stream(mapping.value()).anyMatch(v -> v.startsWith("/api/admin"));
    }

    private static ApiResponse errorResponse(String description) {
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA);
        return new ApiResponse()
            .description(description)
            .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }
}
