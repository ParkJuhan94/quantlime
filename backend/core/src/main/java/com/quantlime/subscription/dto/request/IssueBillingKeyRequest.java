package com.quantlime.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// installmentMonths: 0=일시불, 2~12=할부개월(그 외 값은 서비스 단에서 거부)
public record IssueBillingKeyRequest(
    @NotBlank(message = "authKey는 필수입니다.") String authKey,
    @NotBlank(message = "플랜 코드는 필수입니다.") String planCode,
    @NotNull(message = "할부 개월은 필수입니다.") Integer installmentMonths
) {
}
