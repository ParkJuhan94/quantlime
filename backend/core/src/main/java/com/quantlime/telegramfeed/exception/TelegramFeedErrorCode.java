package com.quantlime.telegramfeed.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TelegramFeedErrorCode implements ErrorCode {

    NOT_FOUND_CHANNEL("해당 채널을 찾을 수 없습니다.", "TF_000"),
    NOT_FOUND_POST("해당 텔레그램 글을 찾을 수 없습니다.", "TF_001"),
    INVALID_POST_STATUS("올바른 글 상태를 입력해주세요.", "TF_002"),
    TELEGRAM_JOB_IN_PROGRESS("이미 실행 중인 텔레그램 수집 작업이 있습니다.", "TF_003"),
    RETENTION_JOB_IN_PROGRESS("이미 실행 중인 데이터 정리 작업이 있습니다.", "TF_004");

    private final String message;
    private final String code;
}
