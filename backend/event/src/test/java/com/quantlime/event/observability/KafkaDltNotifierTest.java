package com.quantlime.event.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.slack.SlackWebhookClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 카운터 이름·태그는 Alertmanager 규칙({@code increase(dlt_messages_total[15m])},
 * {@code $labels.domain}/{@code $labels.topic})과의 계약이라, 바뀌면 알림이 조용히 멈춘다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class KafkaDltNotifierTest {

    @Mock
    private SlackWebhookClient slackWebhookClient;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("[notify는 dlt.messages 카운터를 domain/topic 태그로 증가시키고 Slack 운영 알림을 보낸다]")
    void notify_incrementsCounterWithTagsAndSendsOpsMessage() {
        // given
        KafkaDltNotifier notifier = new KafkaDltNotifier(registry, slackWebhookClient);

        // when
        notifier.notify("payment-webhook", "payment.webhook.received", "payloadHash=abc");

        // then
        double count = registry.get("dlt.messages")
            .tags("domain", "payment-webhook", "topic", "payment.webhook.received")
            .counter().count();
        assertThat(count).isEqualTo(1.0);
        verify(slackWebhookClient).sendOpsMessage(contains("payment-webhook"));
    }

    @Test
    @DisplayName("[notify는 Slack 전송이 실패해도 예외를 전파하지 않고 카운터는 이미 증가해 있다]")
    void notify_slackFails_doesNotPropagateAndCounterStillIncremented() {
        // given
        KafkaDltNotifier notifier = new KafkaDltNotifier(registry, slackWebhookClient);
        willThrow(new RuntimeException("slack down")).given(slackWebhookClient).sendOpsMessage(anyString());

        // when & then
        assertThatCode(() -> notifier.notify("telegram-digest", "telegram.digest.generation.requested", "x"))
            .doesNotThrowAnyException();
        double count = registry.get("dlt.messages")
            .tags("domain", "telegram-digest", "topic", "telegram.digest.generation.requested")
            .counter().count();
        assertThat(count).isEqualTo(1.0);
    }
}
