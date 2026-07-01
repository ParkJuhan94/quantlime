package com.quantlab.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorResponseTemplate handleMethodArgumentNotValidException(
        MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fieldError -> fieldError.getDefaultMessage())
            .orElse("잘못된 요청입니다.");
        log.warn("유효성 검증 실패: message={}", message);
        return new ErrorResponseTemplate(message, "VALIDATION_ERROR");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ValidationException.class)
    public ErrorResponseTemplate handleValidationException(
        ValidationException e) {
        log.warn("검증 예외: message={}, code={}",
            e.getMessage(), e.getCode());
        return new ErrorResponseTemplate(e.getMessage(), e.getCode());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NotFoundException.class)
    public ErrorResponseTemplate handleNotFoundException(
        NotFoundException e) {
        log.warn("리소스 없음: message={}, code={}",
            e.getMessage(), e.getCode());
        return new ErrorResponseTemplate(e.getMessage(), e.getCode());
    }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(ExternalApiException.class)
    public ErrorResponseTemplate handleExternalApiException(
        ExternalApiException e) {
        log.error("외부 API 오류: message={}, code={}",
            e.getMessage(), e.getCode(), e);
        return new ErrorResponseTemplate(e.getMessage(), e.getCode());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResponseTemplate handleException(Exception e) {
        log.error("서버 오류: message={}", e.getMessage(), e);
        return new ErrorResponseTemplate(
            "서버 내부 오류가 발생했습니다.", "INTERNAL_ERROR");
    }
}
