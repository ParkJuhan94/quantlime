package com.quantlime.batch.common;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BatchErrorCode implements ErrorCode {

    NOT_FOUND_EXECUTION("해당 배치 실행 이력을 찾을 수 없습니다.", "BT_000");

    private final String message;
    private final String code;
}
