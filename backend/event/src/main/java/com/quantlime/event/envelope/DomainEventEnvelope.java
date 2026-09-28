package com.quantlime.event.envelope;

import java.time.Instant;
import java.util.UUID;

/**
 * 이 프로젝트의 모든 Kafka 메시지가 공통으로 갖는 봉투 필드. 도메인 payload(예:
 * videoId)는 각 메시지 레코드가 별도로 담고, 이 인터페이스는 "언제·무엇이·어떤
 * 버전으로" 발행됐는지만 표준화한다 - 특정 페이로드 타입으로 감싸는 제네릭
 * 래퍼가 아니라 메시지 레코드가 직접 구현하는 형태라, 토픽별 JsonDeserializer
 * 설정은 기존과 동일하게 유지된다(다형성 역직렬화가 필요 없음).
 */
public interface DomainEventEnvelope {

    UUID eventId();

    Instant occurredAt();

    int version();
}
