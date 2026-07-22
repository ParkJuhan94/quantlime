package com.quantlime.infra.slack.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SlackApiErrorCode implements ErrorCode {

    WEBHOOK_NOT_CONFIGURED("Slack 웹훅이 설정되지 않았습니다.", "SLACK_000"),
    WEBHOOK_SEND_FAILED("Slack 메시지 전송에 실패했습니다.", "SLACK_001");

    private final String message;
    private final String code;
}
