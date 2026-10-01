package com.quantlime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.quantlime.event.market.PriceRefreshRequestedMessage;
import com.quantlime.event.subscription.SubscriptionRenewalDueMessage;
import com.quantlime.market.service.PriceRefreshBatchGate;
import com.quantlime.score.domain.PeerGroup;
import com.quantlime.support.ApiTestSupport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 실제 브로커·실제 컨슈머로 <b>재시도 → DLT → {@code @DltHandler}</b> 전 과정을 검증한다(2026-10-01,
 * Kafka 점검). 운영 백오프(30s→90s→270s)로는 6분이 넘어 이전엔 자동화할 수 없었는데,
 * {@code RetryBackoff}를 프로퍼티화하고 테스트 프로파일에서 300ms로 줄여 몇 초에 끝난다.
 *
 * <p>실패는 모킹 없이 실제 코드 경로에서 만든다 - 존재하지 않는 종목/구독 id는 서비스가
 * {@code NotFoundException}을 던져 재시도 대상이 된다.
 *
 * <p>{@code @TestPropertySource}는 다른 브로커 왕복 통합 테스트와 같은 값이라 Spring 테스트 컨텍스트
 * 캐시를 공유한다(컨텍스트마다 컨슈머가 같은 그룹에 붙어 리밸런싱 폭주가 났던 전례 -
 * application-test.yml 주석).
 */
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=true")
@Tag("integration")
class KafkaRetryDltEndToEndTest extends ApiTestSupport {

    private static final String PRICE_TOPIC = "price.refresh.requested";
    private static final String RENEWAL_TOPIC = "subscription.renewal.due";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private PriceRefreshBatchGate batchGate;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private long endOffset(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            return consumer.endOffsets(List.of(partition), Duration.ofSeconds(10)).get(partition);
        }
    }

    @Test
    @DisplayName("[처리가 계속 실패하는 가격 갱신 메시지는 백오프 간격으로 재시도된 뒤 DLT 핸들러에 도달해 "
        + "팬인에 완료를 통지하고 DLT 카운터를 올린다]")
    void priceRefresh_failingMessage_retriesThenReachesDltHandler() throws Exception {
        // given - 존재하지 않는 종목: 컨슈머가 매번 NotFoundException으로 실패한다
        String runId = "e2e-" + UUID.randomUUID();
        batchGate.startBatch(runId, PeerGroup.DOMESTIC, 1);
        Counter dltCounter = meterRegistry.get("dlt.messages")
            .tags("domain", "market-price-refresh", "topic", PRICE_TOPIC).counter();
        double before = dltCounter.count();
        PriceRefreshRequestedMessage message = new PriceRefreshRequestedMessage(
            UUID.randomUUID(), Instant.now(), 1, runId, "NOPE00", "domestic", null);

        // when
        Instant sentAt = Instant.now();
        kafkaTemplate.send(PRICE_TOPIC, "NOPE00", message).get(10, TimeUnit.SECONDS);

        // then - 재시도 3번(각 300ms)을 거친 뒤에야 DLT 핸들러가 호출된다
        await().atMost(Duration.ofSeconds(60))
            .untilAsserted(() -> assertThat(dltCounter.count()).isEqualTo(before + 1));
        assertThat(Duration.between(sentAt, Instant.now()))
            .as("즉시 DLT로 갔다면 백오프(300ms x 3)가 적용되지 않은 것")
            .isGreaterThanOrEqualTo(Duration.ofMillis(800));
        // DLT로 간 종목도 배치를 막지 않도록 완료가 통지됐다
        assertThat(batchGate.awaitCompletion(runId, PeerGroup.DOMESTIC, Duration.ZERO)).isTrue();
    }

    @Test
    @DisplayName("[DLT 핸들러 자신이 내부에서 실패해도(구독 조회 실패) 같은 DLT로 무한 재발행되지 않는다 - "
        + "9/30 무한 재발행 루프 사고의 회귀 테스트]")
    void subscriptionRenewal_dltHandlerInternalFailure_doesNotRepublishInLoop() throws Exception {
        // given - 존재하지 않는 구독: chargeRenewal도, DLT 핸들러의 handleRenewalRetriesExhausted도 NotFoundException
        String dltTopic = RENEWAL_TOPIC + "-dlt";
        long before = endOffset(dltTopic);

        // when
        kafkaTemplate.send(RENEWAL_TOPIC, "999999", SubscriptionRenewalDueMessage.of(999_999L))
            .get(10, TimeUnit.SECONDS);

        // then - 재시도 소진 후 DLT에 정확히 1건이 도달한다
        await().atMost(Duration.ofSeconds(60)).until(() -> endOffset(dltTopic) > before);
        // 핸들러 실패가 DLT로 다시 발행되는 루프라면 이후에도 오프셋이 계속 증가한다
        Thread.sleep(3000);
        assertThat(endOffset(dltTopic)).isEqualTo(before + 1);
    }
}
