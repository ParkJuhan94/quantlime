package com.quantlime.infra.telegram.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TelegramApiErrorCode implements ErrorCode {

    PREVIEW_FETCH_FAILED("텔레그램 미리보기 페이지 조회에 실패했습니다.", "TG_000"),
    PREVIEW_PARSE_FAILED("텔레그램 미리보기 페이지 파싱에 실패했습니다.", "TG_001");

    private final String message;
    private final String code;
}
