package com.quantlime.feed.dto.mapper;

import com.quantlime.feed.domain.FeedComment;
import com.quantlime.feed.domain.FeedPost;
import com.quantlime.feed.dto.response.FeedCommentResponse;
import com.quantlime.feed.dto.response.FeedPostResponse;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class FeedMapper {

    public static FeedPostResponse toFeedPostResponse(
        FeedPost post, long likeCount, long commentCount, boolean likedByMe, boolean mine) {
        return new FeedPostResponse(
            post.getId(),
            post.getUser().getNickname(),
            post.getUser().getProfileImageUrl(),
            post.getCategory().getLabel(),
            post.getTitle(),
            post.getImageUrl(),
            likeCount,
            commentCount,
            likedByMe,
            mine,
            post.getCreatedAt()
        );
    }

    public static FeedCommentResponse toFeedCommentResponse(FeedComment comment) {
        return new FeedCommentResponse(
            comment.getId(),
            comment.getUser().getNickname(),
            comment.getUser().getProfileImageUrl(),
            comment.getContent(),
            comment.getCreatedAt()
        );
    }
}
