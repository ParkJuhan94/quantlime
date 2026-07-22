package com.quantlime.feed.dto.response;

import java.time.LocalDateTime;

public record FeedPostResponse(
    Long id,
    String nickname,
    String profileImageUrl,
    String category,
    String title,
    String imageUrl,
    long likeCount,
    long commentCount,
    boolean likedByMe,
    // 로그인한 사용자 본인이 쓴 글인지 - 프론트가 이 값으로 수정/삭제
    // 버튼 노출 여부를 결정한다. 비로그인이면 항상 false.
    boolean mine,
    LocalDateTime createdAt
) {
}
