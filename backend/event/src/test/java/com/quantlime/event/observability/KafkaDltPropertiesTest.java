package com.quantlime.event.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 설정 키({@code kafka.dlt.slack-suppressed-domains})가 운영 프로파일 yml과 실제로 바인딩되는지 고정한다 -
 * 키 이름이 어긋나면 억제가 조용히 안 걸려 Slack이 다시 쏟아진다.
 */
@Tag("unit")
class KafkaDltPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(KafkaDltConfig.class);

    @Test
    @DisplayName("[설정이 없으면 억제 도메인은 빈 집합이다]")
    void defaultsToEmptySet() {
        runner.run(context -> assertThat(context.getBean(KafkaDltProperties.class).slackSuppressedDomains())
            .isEmpty());
    }

    @Test
    @DisplayName("[쉼표로 구분한 도메인 목록이 집합으로 바인딩된다]")
    void bindsCommaSeparatedDomains() {
        runner
            .withPropertyValues("kafka.dlt.slack-suppressed-domains=videofeed-transcript, telegram-digest")
            .run(context -> assertThat(context.getBean(KafkaDltProperties.class).slackSuppressedDomains())
                .containsExactlyInAnyOrder("videofeed-transcript", "telegram-digest"));
    }
}
