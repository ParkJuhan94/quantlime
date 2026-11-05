package com.quantlime.infra.toss.realtime;

import lombok.Getter;
import org.springframework.lang.Nullable;

/** 웹소켓 연결 실패. {@code statusCode}는 핸드셰이크 응답 코드이고, 응답조차 없었으면 0이다. */
@Getter
public class TossRealtimeHandshakeException extends RuntimeException {

    private final int statusCode;

    public TossRealtimeHandshakeException(int statusCode, @Nullable Throwable cause) {
        super("토스 웹소켓 연결 실패: status=" + statusCode, cause);
        this.statusCode = statusCode;
    }
}
