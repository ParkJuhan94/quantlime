package com.quantlime.videofeed.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static lombok.AccessLevel.PROTECTED;

// P3(자막 수집)에서 채울 스키마 - 현재는 도메인/리포지토리만 준비해둔다.
@Entity
@Table(name = "transcript", uniqueConstraints = {
    @UniqueConstraint(name = "uk_transcript", columnNames = {"video_id"})
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Transcript extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transcript_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private Video video;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "lang", nullable = false, length = 10)
    private String lang;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Builder
    private Transcript(Video video, String source, String lang, String content, int charCount) {
        validateTranscript(video, source, lang, content);
        this.video = video;
        this.source = source;
        this.lang = lang;
        this.content = content;
        this.charCount = charCount;
    }

    public static Transcript of(Video video, String source, String lang, String content, int charCount) {
        return Transcript.builder()
            .video(video)
            .source(source)
            .lang(lang)
            .content(content)
            .charCount(charCount)
            .build();
    }

    private void validateTranscript(Video video, String source, String lang, String content) {
        Assert.notNull(video, "영상은 필수입니다.");
        Assert.hasText(source, "자막 출처는 필수입니다.");
        Assert.hasText(lang, "언어는 필수입니다.");
        Assert.hasText(content, "자막 내용은 필수입니다.");
    }
}
