package com.quantlime.feed.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeedErrorCode implements ErrorCode {

    INVALID_CATEGORY("존재하지 않는 커뮤니티 주제입니다.", "FEED_000"),
    POST_NOT_FOUND("존재하지 않는 게시글입니다.", "FEED_001");

    private final String message;
    private final String code;
}
