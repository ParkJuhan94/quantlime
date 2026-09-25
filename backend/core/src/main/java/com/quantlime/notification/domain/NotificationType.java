package com.quantlime.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {

    ADMIN_NOTICE("관리자 공지"),
    PAYMENT_SUCCESS("결제 성공"),
    PAYMENT_FAILED("결제 실패"),
    SUBSCRIPTION_EXPIRED("구독 만료"),
    SCORE_RANKING_GLOBAL("전체 스코어 랭킹"),
    SCORE_RANKING_WATCHLIST("관심종목 스코어 랭킹"),
    // 범용 시스템 알림 - 이번 스코프에서는 구체 트리거를 연결하지 않고
    // 발송 API만 열어둔다(다른 도메인이 필요할 때 개별 연결).
    SYSTEM("시스템 알림");

    private final String label;
}
