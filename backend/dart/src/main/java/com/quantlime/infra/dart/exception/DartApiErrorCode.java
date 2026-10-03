package com.quantlime.infra.dart.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DartApiErrorCode implements ErrorCode {

    CORP_LIST_INQUIRY_FAILED("DART 기업고유번호(상장법인목록) 조회에 실패했습니다.", "DART_000");

    private final String message;
    private final String code;
}
