package com.quantlime.notification.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    NOT_FOUND_NOTIFICATION("알림을 찾을 수 없습니다.", "NT_000");

    private final String message;
    private final String code;
}
