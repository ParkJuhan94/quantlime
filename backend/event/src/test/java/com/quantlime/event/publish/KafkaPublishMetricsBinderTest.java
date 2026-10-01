package com.quantlime.event.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.event.observability.DltTopicCatalog;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class KafkaPublishMetricsBinderTest {

    @Test
    @DisplayName("[기동 시 모든 토픽의 kafka.publish.failures 카운터가 0으로 선등록된다 - 첫 실패도 0→1 증가로 보이게 하기 위함]")
    void bindTo_preRegistersZeroCounterPerTopic() {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        // when
        new KafkaPublishMetricsBinder().bindTo(registry);

        // then
        DltTopicCatalog.domainByTopic().keySet().forEach(topic ->
            assertThat(registry.get("kafka.publish.failures").tag("topic", topic).counter().count())
                .as(topic).isZero());
    }
}
