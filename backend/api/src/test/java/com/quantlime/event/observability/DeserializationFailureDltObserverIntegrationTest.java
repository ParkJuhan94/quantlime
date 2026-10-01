package com.quantlime.event.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.quantlime.support.ApiTestSupport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.TestPropertySource;

/**
 * 역직렬화 불가 메시지(포이즌 필)가 실제 브로커를 거쳐 DLT로 가고, 타입 핸들러가 호출되지 못해도
 * {@link DeserializationFailureDltObserver}가 {@code dlt.messages} 카운터로 알리는지 검증한다
 * (2026-10-01). 2026-10-01 Testcontainers 프로브에서 이 경로가 무알림으로 끝나는 걸 실측해
 * 옵저버를 추가했다 - 이 테스트는 그 사각지대의 회귀 방지다.
 *
 * <p>두 케이스는 실제로 겪을 수 있는 서로 다른 원인이다 - 포맷 불일치(JSON 아님)와 레코드
 * 개명/이동 후 구버전 {@code __TypeId__}가 남은 메시지.
 *
 * <p>{@code @TestPropertySource}는 {@code PaymentWebhookKafkaIntegrationTest}와 같은 값이라
 * Spring 테스트 컨텍스트 캐시를 공유한다 - 컨텍스트마다 컨슈머가 같은 그룹 ID로 붙어
 * 리밸런싱 폭주가 났던 전례(application-test.yml 주석)를 늘리지 않기 위함.
 */
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=true")
@Tag("integration")
class DeserializationFailureDltObserverIntegrationTest extends ApiTestSupport {

    private static final String OBSERVER_GROUP = "dlt-poison-pill-observer";

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Counter dltCounter(String domain, String topic) {
        return meterRegistry.get("dlt.messages").tags("domain", domain, "topic", topic).counter();
    }

    // 옵저버는 auto.offset.reset=latest라 파티션 할당 전에 보낸 메시지는 영영 못 본다.
    private void awaitObserverAssignment() {
        await().atMost(Duration.ofSeconds(30)).until(() -> listenerRegistry.getListenerContainers().stream()
            .filter(container -> OBSERVER_GROUP.equals(container.getGroupId()))
            .anyMatch(this::hasAssignment));
    }

    private boolean hasAssignment(MessageListenerContainer container) {
        var assigned = container.getAssignedPartitions();
        return assigned != null && !assigned.isEmpty();
    }

    private void sendRaw(String topic, byte[] value, String typeId) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, "poison-key", value);
            if (typeId != null) {
                record.headers().add("__TypeId__", typeId.getBytes(StandardCharsets.UTF_8));
            }
            producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("[JSON이 아닌 바이트는 DLT로 직행하고 옵저버가 dlt.messages 카운터를 증가시킨다]")
    void notJsonPayload_isCountedByObserver() throws Exception {
        // given
        awaitObserverAssignment();
        Counter counter = dltCounter("payment-webhook", "payment.webhook.received");
        double before = counter.count();

        // when
        sendRaw("payment.webhook.received", "this-is-not-json".getBytes(StandardCharsets.UTF_8), null);

        // then
        await().atMost(Duration.ofSeconds(20))
            .untilAsserted(() -> assertThat(counter.count()).isEqualTo(before + 1));
    }

    @Test
    @DisplayName("[존재하지 않는 __TypeId__ 클래스(레코드 개명/이동 시나리오)도 옵저버가 카운터로 알린다]")
    void unknownTypeId_isCountedByObserver() throws Exception {
        // given
        awaitObserverAssignment();
        Counter counter = dltCounter("subscription-renewal", "subscription.renewal.due");
        double before = counter.count();

        // when
        sendRaw("subscription.renewal.due", "{\"subscriptionId\":1}".getBytes(StandardCharsets.UTF_8),
            "com.quantlime.event.subscription.OldRenamedMessage");

        // then
        await().atMost(Duration.ofSeconds(20))
            .untilAsserted(() -> assertThat(counter.count()).isEqualTo(before + 1));
    }
}
