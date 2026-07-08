package com.quantlab.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    @ExceptionHandler(ConstraintViolationException.class)
    public ErrorResponseTemplate handleConstraintViolationException(
        ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
            .findFirst()
            .map(violation -> violation.getMessage())
            .orElse("잘못된 요청입니다.");
        log.warn("파라미터 검증 실패: message={}", message);
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

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(UnauthorizedException.class)
    public ErrorResponseTemplate handleUnauthorizedException(
        UnauthorizedException e) {
        log.warn("인증 실패: message={}, code={}",
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

    // 매핑되지 않은 경로 요청(오탈자, 존재하지 않는 리소스, prod 프로파일이라
    // 로드되지 않은 컨트롤러 등)은 Spring 6부터 정적 리소스 핸들러가
    // NoResourceFoundException으로 던진다. 별도 처리가 없으면 아래
    // catch-all(Exception.class)이 이를 삼켜 500으로 응답해버려, 클라이언트
    // 오탈자 같은 4xx 상황이 서버 장애처럼 보이는 문제가 있었다.
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException.class)
    public ErrorResponseTemplate handleNoResourceFoundException(
        NoResourceFoundException e) {
        log.warn("요청 경로 없음: path={}", e.getResourcePath());
        return new ErrorResponseTemplate("요청하신 경로를 찾을 수 없습니다.", "NOT_FOUND");
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResponseTemplate handleException(Exception e) {
        log.error("서버 오류: message={}", e.getMessage(), e);
        return new ErrorResponseTemplate(
            "서버 내부 오류가 발생했습니다.", "INTERNAL_ERROR");
    }
}
