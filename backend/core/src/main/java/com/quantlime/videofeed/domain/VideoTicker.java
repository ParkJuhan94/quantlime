package com.quantlime.videofeed.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static lombok.AccessLevel.PROTECTED;

// P4(AI 요약)에서 채울 스키마 - 종목 태깅 결과(summary.payload의
// mentioned_tickers를 정규화해 조회하기 쉽게 저장).
@Entity
@Table(name = "video_ticker", indexes = {
    @Index(name = "idx_video_ticker_ticker_code", columnList = "ticker_code"),
    @Index(name = "idx_video_ticker_video", columnList = "video_id")
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class VideoTicker extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "video_ticker_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private Video video;

    @Column(name = "ticker_code", nullable = false, length = 10)
    private String tickerCode;

    @Column(name = "ticker_name", length = 100)
    private String tickerName;

    @Column(name = "stance", length = 20)
    private String stance;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Builder
    private VideoTicker(Video video, String tickerCode, String tickerName, String stance, BigDecimal confidence) {
        validateVideoTicker(video, tickerCode);
        this.video = video;
        this.tickerCode = tickerCode;
        this.tickerName = tickerName;
        this.stance = stance;
        this.confidence = confidence;
    }

    public static VideoTicker of(Video video, String tickerCode, String tickerName, String stance,
                                  BigDecimal confidence) {
        return VideoTicker.builder()
            .video(video)
            .tickerCode(tickerCode)
            .tickerName(tickerName)
            .stance(stance)
            .confidence(confidence)
            .build();
    }

    private void validateVideoTicker(Video video, String tickerCode) {
        Assert.notNull(video, "영상은 필수입니다.");
        Assert.hasText(tickerCode, "종목 코드는 필수입니다.");
    }
}
