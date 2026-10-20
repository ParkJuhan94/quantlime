package com.quantlime.notice.domain;

import static lombok.AccessLevel.PROTECTED;

import com.quantlime.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * 공지사항. 알림(Notification)은 사용자별 행이고 14일 뒤 삭제되지만, 공지는 한 건이 영구 보관돼
 * 알림에서 링크(/notices/{id})로 다시 열어볼 수 있다. 본문은 일반 텍스트(줄바꿈 유지)다.
 */
@Entity
@Table(name = "notice")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Notice extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    private Notice(String title, String content) {
        validate(title, content);
        this.title = title;
        this.content = content;
    }

    public static Notice of(String title, String content) {
        return new Notice(title, content);
    }

    public void update(String title, String content) {
        validate(title, content);
        this.title = title;
        this.content = content;
    }

    private void validate(String title, String content) {
        Assert.hasText(title, "제목은 필수입니다.");
        Assert.hasText(content, "내용은 필수입니다.");
    }
}
