package com.quantlime.event.subscription;

/** 구독 갱신 재시도 이벤트화(2026-09-24, 카프카 다도메인 확장 Phase 2)가 쓰는 토픽. */
public final class SubscriptionTopics {

    public static final String SUBSCRIPTION_RENEWAL_DUE = "subscription.renewal.due";

    private SubscriptionTopics() {
    }
}
