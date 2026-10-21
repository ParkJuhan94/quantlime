package com.quantlime.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

public record ErrorResponseTemplate(
    @Nullable @Schema(description = "사용자에게 보여줄 수 있는 에러 메시지", example = "유효하지 않은 토큰입니다.")
    String message,
    @Schema(description = "도메인별 에러 코드(`AU_000`처럼 `{도메인 접두사}_{번호}`). 클라이언트 분기에는 메시지가 아니라 이 값을 쓴다", example = "AU_000")
    String code
) {
}
