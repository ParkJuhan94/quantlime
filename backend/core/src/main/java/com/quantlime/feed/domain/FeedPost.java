package com.quantlime.feed.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import com.quantlime.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * 피드(커뮤니티) 글 - 지금은 제목 한 줄만 남긴다(실제 토스증권 피드
 * 글쓰기 모달 구조를 참고, FeedComposeModal 참고). 좋아요/댓글은 이
 * 엔티티에 카운터 필드로 직접 두지 않고 FeedPostLike/FeedComment
 * 별도 테이블로 분리했다 - 목록 조회 시 글 id들을 묶어 집계 쿼리로
 * 좋아요/댓글 수를 채운다(FeedService.getPosts 참고, N+1 방지).
 */
@Entity
@Table(name = "feed_post")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class FeedPost extends TimeBaseEntity {

    private static final int TITLE_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feed_post_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private FeedCategory category;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    // 업로드된 이미지의 공개 경로(FileStorageService.storeImage 참고) - 첨부
    // 안 한 글이 대부분이라 nullable.
    @Column(name = "image_url")
    private String imageUrl;

    @Builder
    private FeedPost(User user, FeedCategory category, String title, String imageUrl) {
        validateFeedPost(user, category, title);
        this.user = user;
        this.category = category;
        this.title = title;
        this.imageUrl = imageUrl;
    }

    public static FeedPost of(User user, FeedCategory category, String title) {
        return of(user, category, title, null);
    }

    public static FeedPost of(User user, FeedCategory category, String title, String imageUrl) {
        return FeedPost.builder()
            .user(user)
            .category(category)
            .title(title)
            .imageUrl(imageUrl)
            .build();
    }

    public void update(FeedCategory category, String title, String imageUrl) {
        validateFeedPost(this.user, category, title);
        this.category = category;
        this.title = title;
        this.imageUrl = imageUrl;
    }

    private void validateFeedPost(User user, FeedCategory category, String title) {
        Assert.notNull(user, "작성자는 필수입니다.");
        Assert.notNull(category, "주제는 필수입니다.");
        Assert.hasText(title, "제목은 필수입니다.");
        Assert.isTrue(title.length() <= TITLE_MAX_LENGTH, "제목은 " + TITLE_MAX_LENGTH + "자를 넘을 수 없습니다.");
    }
}
