package com.quantlime.telegramfeed.domain;

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

// videofeed.domain.VideoTicker와 구조적으로 동일 - 종목 태깅 결과(P7-4에서
// TelegramSummary.payload의 mentioned_tickers를 정규화해 저장).
@Entity
@Table(name = "telegram_post_ticker", indexes = {
    @Index(name = "idx_telegram_post_ticker_ticker_code", columnList = "ticker_code"),
    @Index(name = "idx_telegram_post_ticker_post", columnList = "telegram_post_id")
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class TelegramPostTicker extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "telegram_post_ticker_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "telegram_post_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private TelegramPost telegramPost;

    @Column(name = "ticker_code", nullable = false, length = 10)
    private String tickerCode;

    @Column(name = "ticker_name", length = 100)
    private String tickerName;

    @Column(name = "stance", length = 20)
    private String stance;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Builder
    private TelegramPostTicker(TelegramPost telegramPost, String tickerCode, String tickerName,
                                String stance, BigDecimal confidence) {
        validateTelegramPostTicker(telegramPost, tickerCode);
        this.telegramPost = telegramPost;
        this.tickerCode = tickerCode;
        this.tickerName = tickerName;
        this.stance = stance;
        this.confidence = confidence;
    }

    public static TelegramPostTicker of(TelegramPost telegramPost, String tickerCode, String tickerName,
                                         String stance, BigDecimal confidence) {
        return TelegramPostTicker.builder()
            .telegramPost(telegramPost)
            .tickerCode(tickerCode)
            .tickerName(tickerName)
            .stance(stance)
            .confidence(confidence)
            .build();
    }

    private void validateTelegramPostTicker(TelegramPost telegramPost, String tickerCode) {
        Assert.notNull(telegramPost, "텔레그램 글은 필수입니다.");
        Assert.hasText(tickerCode, "종목 코드는 필수입니다.");
    }
}
