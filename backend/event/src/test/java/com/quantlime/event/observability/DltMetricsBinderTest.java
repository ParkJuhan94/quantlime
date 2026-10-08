package com.quantlime.event.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.quantlime.infra.slack.SlackWebhookClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class DltMetricsBinderTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("[기동 시 카탈로그의 모든 토픽에 대해 dlt.messages 카운터가 0으로 선등록된다 - 첫 DLT도 0→1 증가로 보이게 하기 위함]")
    void bindTo_preRegistersZeroCountersForEveryCatalogTopic() {
        // when
        new DltMetricsBinder().bindTo(registry);

        // then
        DltTopicCatalog.domainByTopic().forEach((topic, domain) -> {
            double count = registry.get("dlt.messages").tags("domain", domain, "topic", topic).counter().count();
            assertThat(count).as("%s/%s", domain, topic).isZero();
        });
    }

    @Test
    @DisplayName("[선등록된 시리즈를 notify가 그대로 이어서 증가시킨다 - 중복 시리즈가 생기지 않는다]")
    void notify_afterPreRegistration_incrementsSameSeries() {
        // given
        new DltMetricsBinder().bindTo(registry);
        KafkaDltNotifier notifier = new KafkaDltNotifier(registry, mock(SlackWebhookClient.class), new KafkaDltProperties(Set.of()));
        String topic = "payment.webhook.received";

        // when
        notifier.notify(DltTopicCatalog.domainOf(topic), topic, "x");

        // then
        assertThat(registry.find("dlt.messages").counters()).hasSize(DltTopicCatalog.domainByTopic().size());
        double count = registry.get("dlt.messages")
            .tags("domain", "payment-webhook", "topic", topic).counter().count();
        assertThat(count).isEqualTo(1.0);
    }
}
