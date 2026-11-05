package com.quantlime.event.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.quantlime.support.ApiTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * 실제 Redpanda(Testcontainers)에서 오프셋 조회부와 lag 계산을 함께 확인한다. 리스너를 띄우지 않고 수동 컨슈머로 "일부만
 * 커밋한 그룹"을 만들어, 브로커의 커밋 오프셋 기준 lag가 처리 진행과 함께 줄어드는지 본다.
 */
@Tag("integration")
class KafkaConsumerLagMonitorIntegrationTest extends ApiTestSupport {

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("커밋이 없으면 처음부터, 일부 커밋하면 남은 만큼, 모두 커밋하면 0으로 lag를 보고한다")
    void lag_followsCommittedOffsets_onRealBroker() throws Exception {
        // given
        String topic = "lag-it-" + UUID.randomUUID();
        String group = "lag-it-group-" + UUID.randomUUID();
        TopicPartition partition = new TopicPartition(topic, 0);
        try (Admin admin = Admin.create(kafkaAdmin.getConfigurationProperties())) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        }
        for (int i = 0; i < 10; i++) {
            kafkaTemplate.send(topic, "k" + i, "v" + i).get();
        }
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        given(container.getGroupId()).willReturn(group);
        given(container.getAssignedPartitions()).willReturn(List.of(partition));
        KafkaListenerEndpointRegistry registry = mock(KafkaListenerEndpointRegistry.class);
        given(registry.getListenerContainers()).willReturn(List.of(container));
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        KafkaConsumerLagMonitor monitor = new KafkaConsumerLagMonitor(registry,
            new AdminKafkaOffsetReader(kafkaAdmin), meterRegistry, Clock.systemUTC());

        // when / then - 커밋이 한 번도 없는 그룹: 처음부터 센다
        monitor.refresh();
        assertThat(lag(meterRegistry, group, topic)).isEqualTo(10.0);

        // when / then - 4건까지 처리·커밋
        try (KafkaConsumer<String, String> consumer = consumer(group)) {
            consumer.assign(List.of(partition));
            consumer.commitSync(Map.of(partition, new OffsetAndMetadata(4)));
            monitor.refresh();
            assertThat(lag(meterRegistry, group, topic)).isEqualTo(6.0);
            assertThat(meterRegistry.get(KafkaConsumerLagMonitor.COMMITTED).tag("group", group).functionCounter()
                .count()).isEqualTo(4.0);

            // when / then - 모두 처리
            consumer.commitSync(Map.of(partition, new OffsetAndMetadata(10)));
            monitor.refresh();
            assertThat(lag(meterRegistry, group, topic)).isZero();
            assertThat(meterRegistry.get(KafkaConsumerLagMonitor.STALLED).tag("group", group).gauge().value())
                .isZero();
        }
        assertThat(meterRegistry.get(KafkaConsumerLagMonitor.COLLECT_AGE).gauge().value())
            .isLessThan(Duration.ofSeconds(30).toSeconds());

        // 알림 규칙(alerts.yml)이 쓰는 Prometheus 지표 이름이 실제 노출 이름과 같은지 - 이름이 어긋나면 알림이 조용히 죽는다
        String scrape = meterRegistry.scrape();
        assertThat(scrape).contains("kafka_consumer_group_lag{group=\"" + group + "\"")
            .contains("kafka_consumer_group_stalled_seconds{group=\"" + group + "\"")
            .contains("kafka_consumer_group_committed_offsets_total{group=\"" + group + "\"")
            .contains("kafka_consumer_group_lag_collect_age_seconds");
    }

    private static double lag(MeterRegistry meterRegistry, String group, String topic) {
        return meterRegistry.get(KafkaConsumerLagMonitor.LAG).tag("group", group).tag("topic", topic).gauge().value();
    }

    private KafkaConsumer<String, String> consumer(String group) {
        Map<String, Object> config = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(config);
    }
}
