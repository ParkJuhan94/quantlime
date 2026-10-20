package com.quantlime.notice.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NoticeErrorCode implements ErrorCode {

    NOT_FOUND_NOTICE("공지를 찾을 수 없습니다.", "NC_000");

    private final String message;
    private final String code;
}
