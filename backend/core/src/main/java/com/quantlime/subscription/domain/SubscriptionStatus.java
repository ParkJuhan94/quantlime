package com.quantlime.subscription.domain;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.subscription.exception.SubscriptionErrorCode;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubscriptionStatus {

    ACTIVE("구독중"),
    PAST_DUE("결제 실패"),
    EXPIRED("만료");

    private final String label;

    public static SubscriptionStatus of(String label) {
        return Arrays.stream(values())
            .filter(status -> status.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new ValidationException(SubscriptionErrorCode.INVALID_SUBSCRIPTION_STATUS));
    }
}
