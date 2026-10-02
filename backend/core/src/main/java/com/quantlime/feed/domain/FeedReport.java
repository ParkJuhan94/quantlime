package com.quantlime.feed.domain;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static lombok.AccessLevel.PROTECTED;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * 피드 글/댓글 신고 - 사용자당 같은 대상에 한 번만 신고할 수 있다(reporter+target 복합 유니크).
 * 서로 다른 신고자 수가 임계치에 도달하면 대상이 자동 숨김된다({@code FeedModerationService}).
 * 대상 id는 글/댓글 테이블이 달라 FK 없이 (targetType, targetId)로만 참조한다.
 */
@Entity
@Table(name = "feed_report", uniqueConstraints = @UniqueConstraint(
    name = "uk_feed_report_reporter_target", columnNames = {"reporter_id", "target_type", "target_id"}))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class FeedReport extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feed_report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private FeedReportTarget targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private FeedReportReason reason;

    @Builder
    private FeedReport(User reporter, FeedReportTarget targetType, Long targetId, FeedReportReason reason) {
        Assert.notNull(reporter, "신고자는 필수입니다.");
        Assert.notNull(targetType, "신고 대상 종류는 필수입니다.");
        Assert.notNull(targetId, "신고 대상 id는 필수입니다.");
        Assert.notNull(reason, "신고 사유는 필수입니다.");
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
    }

    public static FeedReport of(User reporter, FeedReportTarget targetType, Long targetId, FeedReportReason reason) {
        return FeedReport.builder()
            .reporter(reporter)
            .targetType(targetType)
            .targetId(targetId)
            .reason(reason)
            .build();
    }
}
