package com.quantlime.subscription.dto.response;

// subscription은 아직 구독 이력이 없는 사용자면 null이다. customerKey는
// 구독 여부와 무관하게 항상 내려준다 - 프론트가 카드 등록 위젯을 열기
// 전에 필요하다.
public record SubscriptionMeResponse(
    String customerKey,
    SubscriptionResponse subscription
) {
}
