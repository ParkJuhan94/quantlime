package com.quantlime.feed.dto.response;

import java.time.LocalDateTime;

/** 관리자 검토용 - 숨김 처리된 댓글과 누적 신고 수. */
public record AdminFeedCommentResponse(
    Long id,
    Long postId,
    String nickname,
    String content,
    long reportCount,
    LocalDateTime createdAt
) {
}
