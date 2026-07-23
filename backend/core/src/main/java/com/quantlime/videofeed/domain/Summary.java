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

// P4(AI 요약)에서 채울 스키마 - payload는 §6 요약 JSON 스키마를 그대로
// 문자열로 저장한다(P4 설계가 아직 진행 중이라 타입 고정을 미룸).
@Entity
@Table(name = "summary", uniqueConstraints = {
    @UniqueConstraint(name = "uk_summary", columnNames = {"video_id"})
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Summary extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "summary_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private Video video;

    @Column(name = "model", nullable = false, length = 50)
    private String model;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Column(name = "input_token")
    private Integer inputToken;

    @Column(name = "output_token")
    private Integer outputToken;

    @Builder
    private Summary(Video video, String model, String payload, Integer inputToken, Integer outputToken) {
        validateSummary(video, model, payload);
        this.video = video;
        this.model = model;
        this.payload = payload;
        this.inputToken = inputToken;
        this.outputToken = outputToken;
    }

    public static Summary of(Video video, String model, String payload, Integer inputToken, Integer outputToken) {
        return Summary.builder()
            .video(video)
            .model(model)
            .payload(payload)
            .inputToken(inputToken)
            .outputToken(outputToken)
            .build();
    }

    private void validateSummary(Video video, String model, String payload) {
        Assert.notNull(video, "영상은 필수입니다.");
        Assert.hasText(model, "모델명은 필수입니다.");
        Assert.hasText(payload, "요약 페이로드는 필수입니다.");
    }
}
