package com.quantlime.event.publish;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 모든 도메인 퍼블리셔가 공통으로 쓰는 Kafka 발행 지점(2026-10-01, Kafka 점검).
 * 이전에는 퍼블리셔마다 {@code kafkaTemplate.send(...)}의 결과를 확인하지 않아, 브로커
 * 장애·전송 타임아웃·직렬화 실패가 로그 한 줄 외에 아무 흔적 없이 이벤트를 유실시켰다
 * (결제 웹훅은 이미 200을 응답한 뒤라 Toss 재전송도 없어 복구 불가).
 *
 * <p>두 가지 발행 방식을 구분한다:
 * <ul>
 *   <li>{@link #send} - 비동기. 실패를 호출자에게 던지지 않고 ERROR 로그와
 *       {@code kafka.publish.failures} 카운터로 남긴다(Alertmanager {@code KafkaPublishFailed}).
 *       영상/텔레그램/구독/시장 배치는 다음 사이클에 스스로 복구되므로 발행 루프를 막지 않는다</li>
 *   <li>{@link #sendAndConfirm} - 동기 확인. 호출자가 실패를 알아야 할 때(웹훅 수신)만 쓴다</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventSender {

    static final String FAILURE_COUNTER = "kafka.publish.failures";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    /** 실패 카운터 정의를 한 곳에 둔다 - 실제 실패 시점과 기동 선등록({@link KafkaPublishMetricsBinder})이 같은 시리즈여야 한다. */
    static Counter failureCounter(MeterRegistry registry, String topic) {
        return Counter.builder(FAILURE_COUNTER)
            .tag("topic", topic)
            .description("Kafka 발행 실패(브로커 장애/전송 타임아웃/직렬화 실패) 수")
            .register(registry);
    }

    public void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, payload).whenComplete((result, error) -> {
                if (error != null) {
                    recordFailure(topic, key, error);
                }
            });
        } catch (RuntimeException e) {
            // send() 자체가 던지는 경우(예: 메타데이터 대기 타임아웃, 직렬화 예외)도 호출 스레드를 죽이지 않는다.
            recordFailure(topic, key, e);
        }
    }

    /**
     * 브로커가 받았음을 {@code timeout}까지 기다려 확인한다. 못 받았으면 {@link KafkaPublishException}을
     * 던진다 - 웹훅 컨트롤러가 5xx로 응답해 Toss가 재전송하게 하려는 용도다. 타임아웃 뒤에 뒤늦게 전송이
     * 성공하면 재전송분과 중복될 수 있지만, 컨슈머가 payload 해시로 멱등 처리한다.
     */
    public void sendAndConfirm(String topic, String key, Object payload, Duration timeout) {
        try {
            kafkaTemplate.send(topic, key, payload).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordFailure(topic, key, e);
            throw new KafkaPublishException(topic, e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            recordFailure(topic, key, e);
            throw new KafkaPublishException(topic, e);
        }
    }

    private void recordFailure(String topic, String key, Throwable error) {
        failureCounter(meterRegistry, topic).increment();
        log.error("Kafka 발행 실패(이벤트 유실 가능): topic={}, key={}", topic, key, error);
    }
}
