package com.quantlime.event.publish;

/** {@link KafkaEventSender#sendAndConfirm}가 브로커 전송을 확인하지 못했을 때 던진다. */
public class KafkaPublishException extends RuntimeException {

    public KafkaPublishException(String topic, Throwable cause) {
        super("Kafka 발행을 확인하지 못했습니다: topic=" + topic, cause);
    }
}
