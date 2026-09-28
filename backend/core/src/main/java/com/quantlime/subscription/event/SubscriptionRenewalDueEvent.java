package com.quantlime.subscription.event;

/**
 * 갱신 대상 구독을 발행할 때 쓰는 순수 도메인 이벤트(2026-09-24, 카프카
 * 다도메인 확장 Phase 2) - core는 Kafka를 모른다(videofeed/market과 동일한 원칙).
 */
public record SubscriptionRenewalDueEvent(Long subscriptionId) {
}
