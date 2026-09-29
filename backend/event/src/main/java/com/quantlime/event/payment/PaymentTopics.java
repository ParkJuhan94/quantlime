package com.quantlime.event.payment;

/** 결제 웹훅 수신/처리 분리(2026-09-30, 카프카 다도메인 확장 Phase 3)가 쓰는 토픽. */
public final class PaymentTopics {

    public static final String PAYMENT_WEBHOOK_RECEIVED = "payment.webhook.received";

    private PaymentTopics() {
    }
}
