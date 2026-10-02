package com.quantlime.event.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class KafkaEventSenderTest {

    private static final String TOPIC = "payment.webhook.received";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private KafkaEventSender sender() {
        return new KafkaEventSender(kafkaTemplate, registry);
    }

    private double failureCount() {
        return registry.get("kafka.publish.failures").tag("topic", TOPIC).counter().count();
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<SendResult<String, Object>> sent() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }

    private static CompletableFuture<SendResult<String, Object>> failed(Throwable error) {
        return CompletableFuture.failedFuture(error);
    }

    @Test
    @DisplayName("[send는 전송에 성공하면 실패 카운터를 올리지 않는다]")
    void send_success_recordsNoFailure() {
        // given
        given(kafkaTemplate.send(eq(TOPIC), eq("k"), any())).willReturn(sent());

        // when
        sender().send(TOPIC, "k", "payload");

        // then
        assertThat(registry.find("kafka.publish.failures").counter()).isNull();
    }

    @Test
    @DisplayName("[send는 비동기 전송 실패를 예외로 던지지 않고 실패 카운터로 남긴다 - 발행 루프를 막지 않기 위함]")
    void send_asyncFailure_incrementsCounterWithoutThrowing() {
        // given
        given(kafkaTemplate.send(eq(TOPIC), eq("k"), any()))
            .willReturn(failed(new IllegalStateException("broker down")));

        // when & then
        assertThatCode(() -> sender().send(TOPIC, "k", "payload")).doesNotThrowAnyException();
        assertThat(failureCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[send는 KafkaTemplate.send 자체가 던지는 경우(메타데이터 타임아웃 등)도 삼키고 카운터로 남긴다]")
    void send_throwsSynchronously_isCountedAndSwallowed() {
        // given
        given(kafkaTemplate.send(eq(TOPIC), eq("k"), any())).willThrow(new IllegalStateException("metadata timeout"));

        // when & then
        assertThatCode(() -> sender().send(TOPIC, "k", "payload")).doesNotThrowAnyException();
        assertThat(failureCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[sendAndConfirm은 브로커 확인을 받으면 정상 반환한다]")
    void sendAndConfirm_success_returns() {
        // given
        given(kafkaTemplate.send(eq(TOPIC), eq("k"), any())).willReturn(sent());

        // when & then
        assertThatCode(() -> sender().sendAndConfirm(TOPIC, "k", "payload", Duration.ofSeconds(1)))
            .doesNotThrowAnyException();
        assertThat(registry.find("kafka.publish.failures").counter()).isNull();
    }

    @Test
    @DisplayName("[sendAndConfirm은 전송이 실패하면 KafkaPublishException을 던지고 카운터를 올린다 - 웹훅이 5xx로 응답하게 하려는 것]")
    void sendAndConfirm_failure_throwsAndCounts() {
        // given
        given(kafkaTemplate.send(eq(TOPIC), eq("k"), any()))
            .willReturn(failed(new IllegalStateException("broker down")));

        // when & then
        assertThatThrownBy(() -> sender().sendAndConfirm(TOPIC, "k", "payload", Duration.ofSeconds(1)))
            .isInstanceOf(KafkaPublishException.class)
            .hasMessageContaining(TOPIC);
        assertThat(failureCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[sendAndConfirm은 확인이 제한시간 안에 오지 않으면 타임아웃으로 실패 처리한다]")
    void sendAndConfirm_timeout_throwsAndCounts() {
        // given - 영원히 완료되지 않는 future
        given(kafkaTemplate.send(eq(TOPIC), eq("k"), any())).willReturn(new CompletableFuture<>());

        // when & then
        assertThatThrownBy(() -> sender().sendAndConfirm(TOPIC, "k", "payload", Duration.ofMillis(50)))
            .isInstanceOf(KafkaPublishException.class)
            .hasCauseInstanceOf(TimeoutException.class);
        assertThat(failureCount()).isEqualTo(1.0);
    }
}
