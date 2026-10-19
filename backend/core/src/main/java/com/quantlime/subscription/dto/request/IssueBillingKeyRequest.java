package com.quantlime.subscription.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// installmentMonths: 0=일시불, 2~12=할부개월(그 외 값은 서비스 단에서 거부)
public record IssueBillingKeyRequest(
    @Schema(description = "토스페이먼츠 카드 등록 성공 후 전달되는 authKey", example = "auth_key_from_toss")
    @NotBlank(message = "authKey는 필수입니다.") String authKey,
    @Schema(description = "구독 플랜 코드", allowableValues = {"PLAN_3M", "PLAN_6M", "PLAN_12M"}, example = "PLAN_3M")
    @NotBlank(message = "플랜 코드는 필수입니다.") String planCode,
    @Schema(description = "할부 개월(0=일시불, 2~12=할부)", example = "0")
    @NotNull(message = "할부 개월은 필수입니다.") Integer installmentMonths
) {
}
