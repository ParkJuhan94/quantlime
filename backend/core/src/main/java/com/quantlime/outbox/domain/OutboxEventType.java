package com.quantlime.outbox.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 아웃박스로 발행하는 이벤트 종류. core는 Kafka를 모르므로 토픽 이름은 여기 두지 않고, event 모듈의
 * {@code OutboxMessageRouter}가 종류별로 토픽·메시지를 정한다(switch로 매핑해 새 종류를 추가하면 그쪽이 컴파일 에러로 알려준다).
 *
 * <p>현재는 구독 자동 갱신 하나뿐이다(2026-10-31). 다른 퍼블리셔는 발행이 실패해도 다음 사이클이 같은 이벤트를
 * 다시 만들어 아웃박스가 필요 없다는 점검 결과는 {@code docs/00-sre/SRE.md} §7-1 참고.
 */
@Getter
@RequiredArgsConstructor
public enum OutboxEventType {

    SUBSCRIPTION_RENEWAL_DUE("구독 자동 갱신 결제 요청");

    private final String label;
}
