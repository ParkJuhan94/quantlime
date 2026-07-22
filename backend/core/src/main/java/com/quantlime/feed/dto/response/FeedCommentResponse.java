package com.quantlime.feed.dto.response;

import java.time.LocalDateTime;

public record FeedCommentResponse(
    Long id,
    String nickname,
    String profileImageUrl,
    String content,
    LocalDateTime createdAt
) {
}
