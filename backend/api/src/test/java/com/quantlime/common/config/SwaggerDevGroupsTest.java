package com.quantlime.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.quantlime.common.controller.DevController;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 개발용 Swagger 그룹이 DevController의 모든 경로를 덮는지 본다. dev 프로파일 전용 빈이라 일반 컨텍스트 테스트로는
 * 확인되지 않고, 새 /dev 경로를 추가하고 그룹 패턴을 빠뜨리면 Swagger에서 조용히 사라지기 때문이다.
 */
@Tag("unit")
class SwaggerDevGroupsTest {

    @Test
    @DisplayName("[DevController의 모든 POST 경로는 정확히 하나의 개발용 그룹에 속한다]")
    void everyDevPath_belongsToExactlyOneGroup() {
        SwaggerConfig config = new SwaggerConfig();
        OperationCustomizer operation = mock(OperationCustomizer.class);
        OpenApiCustomizer openApi = mock(OpenApiCustomizer.class);
        List<GroupedOpenApi> groups = List.of(
            config.devMarketApi(operation, openApi),
            config.devBacktestApi(operation, openApi),
            config.devAuthSubscriptionApi(operation, openApi),
            config.devNotificationApi(operation, openApi));
        AntPathMatcher matcher = new AntPathMatcher();

        List<String> devPaths = Stream.of(DevController.class.getDeclaredMethods())
            .map(method -> method.getAnnotation(PostMapping.class))
            .filter(java.util.Objects::nonNull)
            .flatMap(mapping -> Stream.of(mapping.value()))
            .map(path -> "/dev" + path)
            .toList();

        assertThat(devPaths).isNotEmpty();
        for (String path : devPaths) {
            long matched = groups.stream()
                .filter(group -> group.getPathsToMatch().stream().anyMatch(pattern -> matcher.match(pattern, path)))
                .count();
            assertThat(matched).as("개발용 그룹 매칭 수: %s", path).isEqualTo(1);
        }
    }
}
