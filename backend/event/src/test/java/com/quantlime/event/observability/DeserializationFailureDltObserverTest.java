package com.quantlime.event.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.KafkaHeaders;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DeserializationFailureDltObserverTest {

    private static final String DESERIALIZATION_EXCEPTION =
        "org.springframework.kafka.support.serializer.DeserializationException";
    private static final String LISTENER_FAILED =
        "org.springframework.kafka.listener.ListenerExecutionFailedException";

    @Mock
    private KafkaDltNotifier dltNotifier;

    @InjectMocks
    private DeserializationFailureDltObserver observer;

    private ConsumerRecord<String, byte[]> dltRecord(String payload) {
        return new ConsumerRecord<>("payment.webhook.received-dlt", 0, 7L, "key",
            payload.getBytes(StandardCharsets.UTF_8));
    }

    private static void header(ConsumerRecord<String, byte[]> record, String key, String value) {
        record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void originalOffset(ConsumerRecord<String, byte[]> record, long offset) {
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_OFFSET, ByteBuffer.allocate(Long.BYTES).putLong(offset).array());
    }

    @Test
    @DisplayName("[역직렬화 실패(cause)로 DLT에 온 레코드는 카탈로그 도메인/원본 토픽으로 알리고 진짜 원인을 싣는다]")
    void onDltRecord_deserializationFailure_notifiesWithRootCause() {
        // given
        ConsumerRecord<String, byte[]> record = dltRecord("not-json");
        header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, "payment.webhook.received");
        header(record, KafkaHeaders.DLT_EXCEPTION_FQCN, LISTENER_FAILED);
        header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, DESERIALIZATION_EXCEPTION);
        header(record, KafkaHeaders.DLT_EXCEPTION_STACKTRACE,
            LISTENER_FAILED + ": Listener failed\n\tat a.b.C.d(C.java:1)\n"
                + "Caused by: " + DESERIALIZATION_EXCEPTION + ": failed to deserialize\n\tat e.f.G.h(G.java:2)\n"
                + "Caused by: java.lang.ClassNotFoundException: com.quantlime.event.subscription.OldRenamedMessage\n"
                + "\tat i.j.K.l(K.java:3)");
        originalOffset(record, 42L);

        // when
        observer.onDltRecord(record);

        // then
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(dltNotifier).notify(eq("payment-webhook"), eq("payment.webhook.received"), detail.capture());
        assertThat(detail.getValue())
            .contains("포이즌 필")
            .contains("원본 offset=42")
            .contains("rootCause=java.lang.ClassNotFoundException: com.quantlime.event.subscription.OldRenamedMessage");
    }

    @Test
    @DisplayName("[실제 포이즌 필 DLT 레코드 모양(레거시 kafka_exception-*/kafka_original-* 헤더)도 알린다 - Testcontainers 실측 형태]")
    void onDltRecord_legacyHeaderFamily_actualPoisonPillShape_notifies() {
        // given
        ConsumerRecord<String, byte[]> record = dltRecord("not-json");
        header(record, KafkaHeaders.ORIGINAL_TOPIC, "subscription.renewal.due");
        header(record, KafkaHeaders.EXCEPTION_FQCN, DESERIALIZATION_EXCEPTION);
        header(record, KafkaHeaders.EXCEPTION_CAUSE_FQCN, DESERIALIZATION_EXCEPTION);
        header(record, KafkaHeaders.EXCEPTION_MESSAGE, "failed to deserialize");
        header(record, KafkaHeaders.EXCEPTION_STACKTRACE,
            DESERIALIZATION_EXCEPTION + ": failed to deserialize\n\tat a.b.C.d(C.java:1)\n"
                + "Caused by: java.lang.IllegalStateException: No type information in headers and no default type provided");
        record.headers().add(KafkaHeaders.ORIGINAL_OFFSET, ByteBuffer.allocate(Long.BYTES).putLong(9L).array());

        // when
        observer.onDltRecord(record);

        // then
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(dltNotifier).notify(eq("subscription-renewal"), eq("subscription.renewal.due"), detail.capture());
        assertThat(detail.getValue())
            .contains("원본 offset=9")
            .contains("rootCause=java.lang.IllegalStateException: No type information in headers");
    }

    @Test
    @DisplayName("[예외 FQCN 자체가 DeserializationException이어도 알린다]")
    void onDltRecord_deserializationExceptionAsTopLevel_notifies() {
        // given
        ConsumerRecord<String, byte[]> record = dltRecord("not-json");
        header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, "video.selected");
        header(record, KafkaHeaders.DLT_EXCEPTION_FQCN, DESERIALIZATION_EXCEPTION);

        // when
        observer.onDltRecord(record);

        // then
        verify(dltNotifier).notify(eq("videofeed-transcript"), eq("video.selected"), any());
    }

    @Test
    @DisplayName("[처리 로직 실패로 DLT에 온 레코드는 타입 핸들러가 이미 알리므로 중복 알림을 보내지 않는다]")
    void onDltRecord_processingFailure_doesNotNotify() {
        // given
        ConsumerRecord<String, byte[]> record = dltRecord("{\"subscriptionId\":1}");
        header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, "subscription.renewal.due");
        header(record, KafkaHeaders.DLT_EXCEPTION_FQCN, LISTENER_FAILED);
        header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, "java.lang.IllegalStateException");

        // when
        observer.onDltRecord(record);

        // then
        verifyNoInteractions(dltNotifier);
    }

    @Test
    @DisplayName("[예외 헤더가 전혀 없는 DLT 레코드(수동/외부 발행)는 알린다]")
    void onDltRecord_noExceptionHeaders_notifies() {
        // given
        ConsumerRecord<String, byte[]> record = dltRecord("{}");
        header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, "price.refresh.requested");

        // when
        observer.onDltRecord(record);

        // then
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(dltNotifier).notify(eq("market-price-refresh"), eq("price.refresh.requested"), detail.capture());
        assertThat(detail.getValue()).contains("예외 헤더가 없는");
    }

    @Test
    @DisplayName("[카탈로그에 없는 원본 토픽은 domain/topic 태그 모두 unknown으로 닫는다 - 메트릭 카디널리티 방지]")
    void onDltRecord_unknownOriginalTopic_usesUnknownTags() {
        // given
        ConsumerRecord<String, byte[]> record = dltRecord("not-json");
        header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, "some.future.topic");
        header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, DESERIALIZATION_EXCEPTION);

        // when
        observer.onDltRecord(record);

        // then
        verify(dltNotifier).notify(eq("unknown"), eq("unknown"), any());
    }

    @Test
    @DisplayName("[알림 상세에 페이로드 원문을 싣지 않는다 - 결제 웹훅 등의 개인정보 노출 방지]")
    void onDltRecord_neverIncludesPayloadInDetail() {
        // given
        ConsumerRecord<String, byte[]> record = dltRecord("secret-card-1234");
        header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, "payment.webhook.received");
        header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, DESERIALIZATION_EXCEPTION);

        // when
        observer.onDltRecord(record);

        // then
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(dltNotifier).notify(any(), any(), detail.capture());
        assertThat(detail.getValue()).doesNotContain("secret-card-1234");
    }

    @Test
    @DisplayName("[알림 경로(notify)가 실패해도 예외를 전파하지 않는다]")
    void onDltRecord_notifierThrows_doesNotPropagate() {
        // given
        ConsumerRecord<String, byte[]> record = dltRecord("not-json");
        header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, "payment.webhook.received");
        header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, DESERIALIZATION_EXCEPTION);
        willThrow(new RuntimeException("slack down")).given(dltNotifier).notify(any(), any(), any());

        // when & then
        assertThatCode(() -> observer.onDltRecord(record)).doesNotThrowAnyException();
    }
}
