package com.quantlab.common.exception;

public record ErrorResponseTemplate(
    String message,
    String code
) {
}
