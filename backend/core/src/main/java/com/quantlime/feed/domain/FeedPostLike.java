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
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static lombok.AccessLevel.PROTECTED;

/**
 * 피드 글 좋아요 - 사용자당 글 하나에 한 번만 누를 수 있다(user_id +
 * feed_post_id 복합 유니크, Watchlist의 (user_id, stock_id) 패턴과 동일).
 */
@Entity
@Table(name = "feed_post_like", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "feed_post_id"}))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class FeedPostLike extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feed_post_like_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_post_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private FeedPost feedPost;

    @Builder
    private FeedPostLike(User user, FeedPost feedPost) {
        Assert.notNull(user, "사용자는 필수입니다.");
        Assert.notNull(feedPost, "게시글은 필수입니다.");
        this.user = user;
        this.feedPost = feedPost;
    }

    public static FeedPostLike of(User user, FeedPost feedPost) {
        return FeedPostLike.builder()
            .user(user)
            .feedPost(feedPost)
            .build();
    }
}
