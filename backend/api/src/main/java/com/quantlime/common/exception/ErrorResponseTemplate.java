package com.quantlime.common.exception;

public record ErrorResponseTemplate(
    String message,
    String code
) {
}
