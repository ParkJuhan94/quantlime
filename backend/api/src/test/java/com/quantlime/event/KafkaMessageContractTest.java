package com.quantlime.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.event.envelope.DomainEventEnvelope;
import com.quantlime.event.market.PriceRefreshRequestedMessage;
import com.quantlime.event.payment.PaymentWebhookReceivedMessage;
import com.quantlime.event.score.QuadrantAlertRequestedMessage;
import com.quantlime.event.subscription.SubscriptionRenewalDueMessage;
import com.quantlime.event.telegramfeed.TelegramDigestGenerationRequestedMessage;
import com.quantlime.event.videofeed.VideoSelectedMessage;
import com.quantlime.event.videofeed.VideoTranscribedMessage;
import com.quantlime.support.ApiTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * 모든 Kafka 메시지 타입이 <b>실제 애플리케이션 설정(application.yml의 직렬화기/역직렬화기/trusted
 * packages)</b>으로 왕복(직렬화 → 역직렬화)되는지 브로커 없이 검증한다(2026-10-01, Kafka 점검).
 *
 * <p>2026-09-30 {@code com.quantlime.event.payment} 패키지를 trusted packages에 추가하지 않아 컨슈머가
 * 메시지를 하나도 소비하지 못한 사고가 있었다 - 자동화 테스트가 아니라 라이브 호출 중에야 발견됐고,
 * 그때는 DLT 알림도 없었다. 새 메시지 타입이 추가되면 {@link #samples()}에도 추가하도록 아래 스캔
 * 테스트가 강제하므로, 새 패키지를 trusted packages에 빠뜨리면 CI에서 실패한다.
 *
 * <p>{@code LocalDate}/{@code Instant}/{@code UUID} 필드가 손실 없이 왕복되는지도 함께 확인한다.
 */
@Tag("integration")
class KafkaMessageContractTest extends ApiTestSupport {

    @Autowired
    private KafkaProperties kafkaProperties;

    // 새 DomainEventEnvelope 구현체(= Kafka 메시지)를 추가하면 여기에 샘플을 추가한다.
    private static List<Object> samples() {
        return List.of(
            VideoSelectedMessage.of(1L),
            VideoTranscribedMessage.of(2L),
            new PriceRefreshRequestedMessage(UUID.randomUUID(), Instant.now(), 1,
                "run-1", "005930", "domestic", LocalDate.of(2026, 9, 30)),
            // latestScoreDate는 nullable(스냅샷에 값이 없는 종목)
            new PriceRefreshRequestedMessage(UUID.randomUUID(), Instant.now(), 1,
                "run-1", "AAPL", "overseas", null),
            SubscriptionRenewalDueMessage.of(3L),
            PaymentWebhookReceivedMessage.of("hash-1", "{\"eventType\":\"X\"}"),
            TelegramDigestGenerationRequestedMessage.of(4L, LocalDate.of(2026, 9, 30)),
            QuadrantAlertRequestedMessage.of(5L));
    }

    private Object roundTrip(Object message) {
        JsonSerializer<Object> serializer = new JsonSerializer<>();
        serializer.configure(kafkaProperties.buildProducerProperties(null), false);
        ErrorHandlingDeserializer<Object> deserializer = new ErrorHandlingDeserializer<>();
        deserializer.configure(kafkaProperties.buildConsumerProperties(null), false);

        Headers headers = new RecordHeaders();
        byte[] bytes = serializer.serialize("contract-test-topic", headers, message);
        return deserializer.deserialize("contract-test-topic", headers, bytes);
    }

    @Test
    @DisplayName("[모든 메시지 타입이 실제 직렬화/역직렬화 설정(trusted packages 포함)으로 손실 없이 왕복된다]")
    void everyMessageTypeRoundTripsThroughProductionConfig() {
        for (Object sample : samples()) {
            // ErrorHandlingDeserializer는 실패 시 예외 대신 null을 돌려주고 헤더에 예외를 남긴다.
            assertThat(roundTrip(sample))
                .as("%s 왕복 - 실패했다면 trusted packages에 해당 패키지가 있는지 확인", sample.getClass().getSimpleName())
                .isEqualTo(sample);
        }
    }

    @Test
    @DisplayName("[DomainEventEnvelope을 구현한 모든 메시지 타입이 샘플에 포함돼 있다 - 새 타입을 추가하고 계약 테스트를 빠뜨리지 못하게 한다]")
    void samplesCoverEveryMessageType() {
        // given
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(DomainEventEnvelope.class));
        Set<String> declared = scanner.findCandidateComponents("com.quantlime.event").stream()
            .map(BeanDefinition::getBeanClassName)
            .collect(Collectors.toSet());

        // when
        Set<String> covered = samples().stream().map(s -> s.getClass().getName()).collect(Collectors.toSet());

        // then - 스캔이 아무것도 못 찾아 조용히 통과하는 일을 막는 하한(현재 6종)
        assertThat(declared).hasSizeGreaterThanOrEqualTo(6);
        assertThat(covered).containsAll(declared);
    }
}
