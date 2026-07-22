package com.quantlime.feed.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import com.quantlime.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "feed_comment")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class FeedComment extends TimeBaseEntity {

    private static final int CONTENT_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feed_comment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_post_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private FeedPost feedPost;

    @Column(name = "content", nullable = false, length = CONTENT_MAX_LENGTH)
    private String content;

    @Builder
    private FeedComment(User user, FeedPost feedPost, String content) {
        validateFeedComment(user, feedPost, content);
        this.user = user;
        this.feedPost = feedPost;
        this.content = content;
    }

    public static FeedComment of(User user, FeedPost feedPost, String content) {
        return FeedComment.builder()
            .user(user)
            .feedPost(feedPost)
            .content(content)
            .build();
    }

    private void validateFeedComment(User user, FeedPost feedPost, String content) {
        Assert.notNull(user, "작성자는 필수입니다.");
        Assert.notNull(feedPost, "게시글은 필수입니다.");
        Assert.hasText(content, "댓글 내용은 필수입니다.");
        Assert.isTrue(content.length() <= CONTENT_MAX_LENGTH, "댓글은 " + CONTENT_MAX_LENGTH + "자를 넘을 수 없습니다.");
    }
}
