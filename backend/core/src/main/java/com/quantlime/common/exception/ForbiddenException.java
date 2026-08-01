package com.quantlime.common.exception;

import lombok.Getter;

@Getter
public class ForbiddenException extends RuntimeException {

    private final String code;

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }
}
