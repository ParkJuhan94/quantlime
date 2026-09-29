package com.quantlime.payment.event;

/**
 * 서명 검증까지 마친 토스페이먼츠 웹훅을 카프카로 중계하기 위한 순수 도메인
 * 이벤트(2026-09-30, 카프카 다도메인 확장 Phase 3) - core는 Kafka를 모른다
 * (videofeed/market/subscription과 동일한 원칙). {@code payloadHash}는
 * 컨슈머 쪽 중복 처리 방지(멱등) 기준 - 실제 Toss 웹훅 JSON 스키마가 아직
 * 확정되지 않아 필드 기반 멱등키 대신 payload 전체의 SHA-256 해시를 쓴다
 * (PaymentService.handleWebhook 주석 참고).
 */
public record PaymentWebhookReceivedEvent(String payloadHash, String payload) {
}
