package com.quantlime.subscription.event;

import java.time.LocalDate;

/**
 * 갱신 대상 구독의 결제 요청(2026-09-24, 카프카 다도메인 확장 Phase 2) - core는 Kafka를 모른다(videofeed/market과
 * 동일한 원칙). 2026-10-31부터 ApplicationEvent로 바로 발행하지 않고 아웃박스 payload로 저장된다
 * ({@code SubscriptionService.requestRenewal}) - 발행이 실패하면 다음날 조회(결제 예정일 = 오늘)에서 빠져 영구
 * 누락됐기 때문이다. {@code billingDate}는 요청이 가리키는 결제 예정일로, 컨슈머가 지연·중복 전달을 거르는 기준이다.
 */
public record SubscriptionRenewalDueEvent(Long subscriptionId, LocalDate billingDate) {
}
