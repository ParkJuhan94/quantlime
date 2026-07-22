package com.quantlime.common.exception;

import lombok.Getter;

@Getter
public class ExternalApiException extends RuntimeException {

    private final String code;

    public ExternalApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public ExternalApiException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
    }
}
