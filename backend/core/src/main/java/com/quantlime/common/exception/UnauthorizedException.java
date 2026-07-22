package com.quantlime.common.exception;

import lombok.Getter;

@Getter
public class UnauthorizedException extends RuntimeException {

    private final String code;

    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }
}
