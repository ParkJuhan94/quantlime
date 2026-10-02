package com.quantlime.feed.dto.response;

import java.time.LocalDateTime;

/** 관리자 검토용 - 숨김 처리된 글과 누적 신고 수. */
public record AdminFeedPostResponse(
    Long id,
    String nickname,
    String category,
    String title,
    String imageUrl,
    long reportCount,
    LocalDateTime createdAt
) {
}
