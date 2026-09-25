package com.quantlime.notification.domain;

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

// 브로드캐스트(관리자 공지, 전체 스코어 랭킹)도 사용자별로 별도 row를
// 갖는다 - 알림별 읽음/안읽음(isRead)을 사용자마다 독립적으로 추적해야
// 하기 때문(HandsUp의 "User에 읽은개수 카운터" 방식은 브로드캐스트를
// 지원하지 못해 채택하지 않음).
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Notification extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
        foreignKey = @ForeignKey(NO_CONSTRAINT))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Builder
    private Notification(User user, NotificationType type, String title, String content, String linkUrl) {
        validateNotification(user, type, title, content);
        this.user = user;
        this.type = type;
        this.title = title;
        this.content = content;
        this.linkUrl = linkUrl;
        this.isRead = false;
    }

    public static Notification of(User user, NotificationType type, String title, String content, String linkUrl) {
        return Notification.builder()
            .user(user)
            .type(type)
            .title(title)
            .content(content)
            .linkUrl(linkUrl)
            .build();
    }

    public void markAsRead() {
        this.isRead = true;
    }

    private void validateNotification(User user, NotificationType type, String title, String content) {
        Assert.notNull(user, "수신자는 필수입니다.");
        Assert.notNull(type, "알림 종류는 필수입니다.");
        Assert.hasText(title, "제목은 필수입니다.");
        Assert.hasText(content, "내용은 필수입니다.");
    }
}
