package com.quantlime.subscription.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubscriptionErrorCode implements ErrorCode {

    NOT_FOUND_SUBSCRIPTION("구독 정보를 찾을 수 없습니다.", "SUB_000"),
    NOT_FOUND_SUBSCRIPTION_PLAN("존재하지 않는 구독 플랜입니다.", "SUB_001"),
    ALREADY_SUBSCRIBED("이미 구독 중입니다.", "SUB_002"),
    INVALID_SUBSCRIPTION_STATUS("유효하지 않은 구독 상태입니다.", "SUB_003"),
    INVALID_INSTALLMENT_MONTHS("유효하지 않은 할부 개월입니다.", "SUB_004"),
    SUBSCRIPTION_IN_PROGRESS("구독 처리가 진행 중입니다. 잠시 후 다시 시도해주세요.", "SUB_005"),
    PREMIUM_REQUIRED("구독이 필요한 기능입니다.", "SUB_006");

    private final String message;
    private final String code;
}
