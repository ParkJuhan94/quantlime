package com.quantlime.telegramfeed.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import com.quantlime.videofeed.domain.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static lombok.AccessLevel.PROTECTED;

/**
 * 텔레그램 채널 글(Phase 8 P7). 유튜브 Video와 구조적으로 대응하지만
 * 자막 단계가 없고(본문이 이미 텍스트) title/durationSec 자리에
 * content/charCount를 쓴다 - 설계 근거는 docs/ROADMAP.md "Phase 8 P7" 참고.
 */
@Entity
@Table(name = "telegram_post", indexes = {
    @Index(name = "idx_telegram_post_status", columnList = "status"),
    @Index(name = "idx_telegram_post_published_at", columnList = "published_at"),
    @Index(name = "idx_telegram_post_channel_message", columnList = "channel_id, message_id"),
    // TelegramFeedService.findSourcePosts(다이제스트 상세/카운트 조회의
    // 확정 N+1 경로)의 findByChannelAndStatusAndPublishedAtBetween이 이
    // 세 컬럼을 그대로 쓴다 - 기존 단일 컬럼 인덱스로는 이 범위 조건에
    // leftmost prefix가 안 맞는다. 성능 개선 계획 문서 Phase 2 참고.
    @Index(name = "idx_telegram_post_channel_status_published",
        columnList = "channel_id, status, published_at")
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class TelegramPost extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "telegram_post_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private Channel channel;

    // "<채널핸들>/<메시지ID>"(예: insidertracking/61040) - 텔레그램 메시지
    // ID는 채널 내부에서만 유일해 숫자만으로는 채널 간 충돌이 나므로 핸들과
    // 합쳐 전역 유일 키로 쓴다(Video.externalVideoId와 동일한 역할).
    @Column(name = "external_post_id", nullable = false, unique = true, length = 120)
    private String externalPostId;

    // 채널 내 정렬/증분 수집 커서(?after=)용 - externalPostId 문자열에서
    // 매번 파싱하지 않도록 별도 컬럼으로 비정규화.
    @Column(name = "message_id", nullable = false)
    private long messageId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // content.length()에서 항상 유도(생성자에서만 계산) - 별도로 입력받으면
    // content와 어긋날 위험이 있어 아예 그 경로를 없앴다.
    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "view_count_checked_at")
    private LocalDateTime viewCountCheckedAt;

    // MVP는 미디어를 다운로드/저장하지 않고 유무만 플래그로 기록한다.
    @Column(name = "has_media", nullable = false)
    private boolean hasMedia;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TelegramPostStatus status;

    @Builder
    private TelegramPost(Channel channel, String externalPostId, long messageId, String content,
                          LocalDateTime publishedAt, Long viewCount, LocalDateTime viewCountCheckedAt,
                          boolean hasMedia) {
        validateTelegramPost(channel, externalPostId, content, publishedAt);
        this.channel = channel;
        this.externalPostId = externalPostId;
        this.messageId = messageId;
        this.content = content;
        this.charCount = content.length();
        this.publishedAt = publishedAt;
        this.viewCount = viewCount;
        this.viewCountCheckedAt = viewCountCheckedAt;
        this.hasMedia = hasMedia;
        this.status = TelegramPostStatus.DISCOVERED;
    }

    public static TelegramPost of(Channel channel, String externalPostId, long messageId, String content,
                                   LocalDateTime publishedAt, Long viewCount, LocalDateTime viewCountCheckedAt,
                                   boolean hasMedia) {
        return TelegramPost.builder()
            .channel(channel)
            .externalPostId(externalPostId)
            .messageId(messageId)
            .content(content)
            .publishedAt(publishedAt)
            .viewCount(viewCount)
            .viewCountCheckedAt(viewCountCheckedAt)
            .hasMedia(hasMedia)
            .build();
    }

    public void updateViewCount(Long viewCount, LocalDateTime checkedAt) {
        this.viewCount = viewCount;
        this.viewCountCheckedAt = checkedAt;
    }

    public void markFilteredOut() {
        this.status = TelegramPostStatus.FILTERED_OUT;
    }

    public void markSelected() {
        this.status = TelegramPostStatus.SELECTED;
    }

    private void validateTelegramPost(Channel channel, String externalPostId, String content,
                                       LocalDateTime publishedAt) {
        Assert.notNull(channel, "채널은 필수입니다.");
        Assert.hasText(externalPostId, "외부 글 ID는 필수입니다.");
        Assert.hasText(content, "본문은 필수입니다.");
        Assert.notNull(publishedAt, "게시 시각은 필수입니다.");
    }
}
