package com.quantlime.event.observability;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

/**
 * 역직렬화에 실패한 포이즌 필(poison pill)이 DLT에 쌓였을 때 알리는 안전망(2026-10-01).
 *
 * <p><b>왜 필요한가</b>: 각 컨슈머의 {@code @DltHandler}는 메시지를 타입 객체로 역직렬화해야
 * 호출된다. 포맷 불일치·존재하지 않는 {@code __TypeId__}(레코드 개명/이동)·trusted
 * packages 누락처럼 역직렬화 자체가 깨진 메시지는 재시도 없이 DLT로 직행하는데(좋음),
 * DLT 컨슈머도 같은 이유로 역직렬화에 실패해 "No further action will be taken"으로
 * 끝나 {@code @DltHandler}가 영원히 호출되지 않는다 - {@code dlt.messages} 카운터도 Slack
 * 알림도 없이 조용히 DLT에 남는다(로컬 Testcontainers 프로브로 실측, 9/30 trusted
 * packages 누락 사고도 이 경로라 알림 없이 라이브 호출로 발견됐다).
 *
 * <p><b>역할 분담</b>: 이 옵저버는 값을 {@code byte[]}로만 읽어 역직렬화 실패가 원천적으로
 * 없다. 단, 알림은 <i>타입 핸들러가 처리할 수 없는 레코드(역직렬화 실패, 또는 예외
 * 헤더가 아예 없는 레코드)</i>에만 보낸다 - 처리 로직 실패로 DLT에 간 일반 레코드는
 * 각 컨슈머의 {@code @DltHandler}가 이미 같은 {@link KafkaDltNotifier}로 알리므로 여기서
 * 또 알리면 중복이다. 6곳의 알림을 이 옵저버 하나로 합치는 정리는 컨슈머 파일 전반을
 * 건드려야 해서 이번 범위에서 제외했다.
 *
 * <p>{@code auto.offset.reset=latest}인 이유: 이 그룹을 처음 배포하는 시점에 이미 DLT에
 * 쌓여 있는 과거 메시지(테스트 잔여물 등)가 한꺼번에 알림으로 쏟아지는 걸 막는다.
 * 페이로드는 알림에 싣지 않는다 - 결제 웹훅 등에 개인정보/카드 관련 값이 있을 수 있다.
 *
 * <p>절대 예외를 던지지 않는다 - 이 리스너는 {@code @RetryableTopic} 대상이 아니라 실패가
 * DLT로 재발행되지는 않지만, 알림 경로가 스스로 컨슈머 스레드를 죽이면 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeserializationFailureDltObserver {

    private static final String DESERIALIZATION_EXCEPTION =
        "org.springframework.kafka.support.serializer.DeserializationException";
    private static final int MAX_DETAIL_LENGTH = 300;

    // 포이즌 필은 DLPR이 역직렬화 예외 정보를 레거시 계열(kafka_exception-*, kafka_original-*)로 싣고
    // (Testcontainers 실측), 일반 처리 실패는 DLT 계열(kafka_dlt-*)이 기본이다 - 두 계열을 모두 읽는다.
    private static final String[] EXCEPTION_FQCN = {KafkaHeaders.EXCEPTION_FQCN, KafkaHeaders.DLT_EXCEPTION_FQCN};
    private static final String[] EXCEPTION_CAUSE_FQCN =
        {KafkaHeaders.EXCEPTION_CAUSE_FQCN, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN};
    private static final String[] EXCEPTION_STACKTRACE =
        {KafkaHeaders.EXCEPTION_STACKTRACE, KafkaHeaders.DLT_EXCEPTION_STACKTRACE};
    private static final String[] EXCEPTION_MESSAGE =
        {KafkaHeaders.EXCEPTION_MESSAGE, KafkaHeaders.DLT_EXCEPTION_MESSAGE};
    private static final String[] ORIGINAL_TOPIC = {KafkaHeaders.ORIGINAL_TOPIC, KafkaHeaders.DLT_ORIGINAL_TOPIC};
    private static final String[] ORIGINAL_OFFSET = {KafkaHeaders.ORIGINAL_OFFSET, KafkaHeaders.DLT_ORIGINAL_OFFSET};

    private final KafkaDltNotifier dltNotifier;

    @KafkaListener(topicPattern = ".*-dlt", groupId = "dlt-poison-pill-observer",
        properties = {
            "value.deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer",
            "auto.offset.reset=latest"})
    public void onDltRecord(ConsumerRecord<String, byte[]> record) {
        try {
            String exceptionFqcn = headerText(record, EXCEPTION_FQCN);
            String causeFqcn = headerText(record, EXCEPTION_CAUSE_FQCN);
            boolean noExceptionHeaders = exceptionFqcn.isEmpty() && causeFqcn.isEmpty();
            boolean deserializationFailure = DESERIALIZATION_EXCEPTION.equals(exceptionFqcn)
                || DESERIALIZATION_EXCEPTION.equals(causeFqcn);
            if (!deserializationFailure && !noExceptionHeaders) {
                return;
            }

            String originalTopic = headerText(record, ORIGINAL_TOPIC);
            String domain = DltTopicCatalog.domainOf(originalTopic);
            String topicTag = DltTopicCatalog.UNKNOWN.equals(domain) ? DltTopicCatalog.UNKNOWN : originalTopic;
            String reason = deserializationFailure
                ? "역직렬화 불가 메시지(포이즌 필) - 재시도 없이 DLT로 직행했고 타입 핸들러는 실행되지 않음"
                : "예외 헤더가 없는 DLT 레코드(수동 발행 또는 외부 프로듀서로 추정)";
            String detail = reason + ". " + location(record, originalTopic) + ", rootCause=" + rootCause(record)
                + ". 페이로드는 개인정보 가능성이 있어 알림에 싣지 않음 - rpk topic consume "
                + record.topic() + " 로 확인";
            log.warn("DLT 포이즌 필 감지: domain={}, {}", domain, detail);
            dltNotifier.notify(domain, topicTag, detail);
        } catch (Exception e) {
            log.error("포이즌 필 DLT 옵저버 자체 실패(예외를 삼킴): topic={}, offset={}",
                record.topic(), record.offset(), e);
        }
    }

    private static String location(ConsumerRecord<String, byte[]> record, String originalTopic) {
        String originalOffset = "?";
        for (String key : ORIGINAL_OFFSET) {
            Header offset = record.headers().lastHeader(key);
            if (offset != null && offset.value() != null && offset.value().length == Long.BYTES) {
                originalOffset = String.valueOf(ByteBuffer.wrap(offset.value()).getLong());
                break;
            }
        }
        return "원본 토픽=" + (originalTopic.isEmpty() ? "?" : originalTopic) + ", 원본 offset=" + originalOffset
            + ", DLT=" + record.topic() + "[" + record.partition() + "]@" + record.offset();
    }

    // 스택트레이스 헤더의 마지막 "Caused by:" 줄이 진짜 원인이다(최상위 메시지는 "failed to
    // deserialize"/"Listener failed" 수준이라 정보가 없다). 없으면 예외 메시지 헤더로 대체한다.
    private static String rootCause(ConsumerRecord<String, byte[]> record) {
        String stacktrace = headerText(record, EXCEPTION_STACKTRACE);
        int idx = stacktrace.lastIndexOf("Caused by: ");
        String line = idx >= 0
            ? stacktrace.substring(idx + "Caused by: ".length()).lines().findFirst().orElse("")
            : headerText(record, EXCEPTION_MESSAGE);
        if (line.isEmpty()) {
            return "?";
        }
        return line.length() > MAX_DETAIL_LENGTH ? line.substring(0, MAX_DETAIL_LENGTH) + "..." : line;
    }

    /** 주어진 헤더 키들 중 값이 있는 첫 번째를 문자열로 읽는다(없으면 빈 문자열). */
    private static String headerText(ConsumerRecord<String, byte[]> record, String... keys) {
        for (String key : keys) {
            Header header = record.headers().lastHeader(key);
            if (header != null && header.value() != null) {
                return new String(header.value(), StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}
