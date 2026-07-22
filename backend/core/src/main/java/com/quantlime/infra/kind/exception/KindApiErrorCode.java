package com.quantlime.infra.kind.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KindApiErrorCode implements ErrorCode {

    CORP_LIST_INQUIRY_FAILED("KIND 상장법인목록 조회에 실패했습니다.", "KIND_000");

    private final String message;
    private final String code;
}
