package com.quantlime.videofeed.domain;

import com.quantlime.common.domain.TimeBaseEntity;
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

@Entity
@Table(name = "video", indexes = {
    @Index(name = "idx_video_status", columnList = "status"),
    @Index(name = "idx_video_published_at", columnList = "published_at")
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Video extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "video_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private Channel channel;

    // 유튜브가 발급한 외부 영상 식별자(11자) - PK 컬럼명(video_id)과
    // 겹치지 않도록 external_video_id로 구분(Channel.externalChannelId와
    // 동일한 이유).
    @Column(name = "external_video_id", nullable = false, unique = true, length = 50)
    private String externalVideoId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "view_count_checked_at")
    private LocalDateTime viewCountCheckedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VideoStatus status;

    @Column(name = "fail_reason", columnDefinition = "TEXT")
    private String failReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Builder
    private Video(Channel channel, String externalVideoId, String title, LocalDateTime publishedAt,
                  Integer durationSec, Long viewCount, LocalDateTime viewCountCheckedAt) {
        validateVideo(channel, externalVideoId, title, publishedAt);
        this.channel = channel;
        this.externalVideoId = externalVideoId;
        this.title = title;
        this.publishedAt = publishedAt;
        this.durationSec = durationSec;
        this.viewCount = viewCount;
        this.viewCountCheckedAt = viewCountCheckedAt;
        this.status = VideoStatus.DISCOVERED;
        this.retryCount = 0;
    }

    public static Video of(Channel channel, String externalVideoId, String title,
                            LocalDateTime publishedAt, Integer durationSec, Long viewCount,
                            LocalDateTime viewCountCheckedAt) {
        return Video.builder()
            .channel(channel)
            .externalVideoId(externalVideoId)
            .title(title)
            .publishedAt(publishedAt)
            .durationSec(durationSec)
            .viewCount(viewCount)
            .viewCountCheckedAt(viewCountCheckedAt)
            .build();
    }

    public void markFilteredOut() {
        this.status = VideoStatus.FILTERED_OUT;
    }

    public void markPendingReview() {
        this.status = VideoStatus.PENDING_REVIEW;
    }

    public void markSelected() {
        this.status = VideoStatus.SELECTED;
    }

    public void markTranscribed() {
        this.status = VideoStatus.TRANSCRIBED;
    }

    public void markSummarized() {
        this.status = VideoStatus.SUMMARIZED;
    }

    public void markFailed(String failReason) {
        this.status = VideoStatus.FAILED;
        this.failReason = failReason;
        this.retryCount++;
    }

    private void validateVideo(Channel channel, String externalVideoId, String title,
                                LocalDateTime publishedAt) {
        Assert.notNull(channel, "채널은 필수입니다.");
        Assert.hasText(externalVideoId, "외부 영상 ID는 필수입니다.");
        Assert.hasText(title, "제목은 필수입니다.");
        Assert.notNull(publishedAt, "게시 시각은 필수입니다.");
    }
}
