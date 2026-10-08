package com.quantlime.event.observability;

import com.quantlime.infra.slack.SlackWebhookClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 모든 도메인의 {@code @DltHandler}가 공통으로 호출하는 DLT 관측 지점(2026-09-24,
 * 카프카 다도메인 확장). 하이브리드 알림 중 "앱 경로"를 담당한다 - 즉시·상세
 * Slack 메시지 + Prometheus 카운터(`dlt_messages_total`) 증가. 카운터는
 * Alertmanager 규칙(`quantlime-kafka-dlt`)이 "앱 자체가 죽어 Slack 호출도 못한
 * 경우"까지 잡는 백스톱 근거로 쓴다.
 *
 * <p>Slack 전송 실패가 DLT 처리 자체를 막아서는 안 되므로 예외를 삼킨다 -
 * 이미 최종 실패해 DLT로 온 메시지이고, 카운터 증가는 Slack 성패와 무관하게
 * 먼저 끝나 있어 Alertmanager 백스톱은 계속 유효하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDltNotifier {

    static final String COUNTER_NAME = "dlt.messages";

    private final MeterRegistry meterRegistry;
    private final SlackWebhookClient slackWebhookClient;
    private final KafkaDltProperties properties;

    /** 카운터 정의를 한 곳에 둔다 - 알림 사유별로 등록 위치(실제 발생/기동 선등록)가 달라도 같은 시리즈여야 한다. */
    static Counter counter(MeterRegistry registry, String domain, String topic) {
        return Counter.builder(COUNTER_NAME)
            .tag("domain", domain)
            .tag("topic", topic)
            .description("Kafka DLT(Dead Letter Topic)로 이관된 메시지 수")
            .register(registry);
    }

    public void notify(String domain, String topic, String detail) {
        counter(meterRegistry, domain, topic).increment();

        // 카운터는 위에서 이미 증가했으므로 Alertmanager 집계 알림/라우팅은 영향받지 않는다 - 개별 Slack 메시지만 끈다
        if (properties.slackSuppressedDomains().contains(domain)) {
            log.info("DLT Slack 알림 억제(kafka.dlt.slack-suppressed-domains): domain={}, topic={}", domain, topic);
            return;
        }

        try {
            slackWebhookClient.sendOpsMessage(
                "[DLT] " + domain + " (topic=" + topic + ")\n" + detail);
        } catch (Exception e) {
            log.error("DLT Slack 알림 전송 실패: domain={}, topic={}", domain, topic, e);
        }
    }
}
