package com.quantlime.payment.domain;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.payment.exception.PaymentErrorCode;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    DONE("결제 완료"),
    FAILED("결제 실패"),
    CANCELED("결제 취소");

    private final String label;

    public static PaymentStatus of(String label) {
        return Arrays.stream(values())
            .filter(status -> status.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new ValidationException(PaymentErrorCode.NOT_FOUND_PAYMENT));
    }
}
