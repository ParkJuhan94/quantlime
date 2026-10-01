package com.quantlime.feed.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeedErrorCode implements ErrorCode {

    INVALID_CATEGORY("존재하지 않는 커뮤니티 주제입니다.", "FEED_000"),
    POST_NOT_FOUND("존재하지 않는 게시글입니다.", "FEED_001"),
    COMMENT_NOT_FOUND("존재하지 않는 댓글입니다.", "FEED_003"),
    CANNOT_REPORT_OWN_CONTENT("본인이 작성한 글/댓글은 신고할 수 없습니다.", "FEED_004");

    private final String message;
    private final String code;
}
