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

// TelegramPostTicker(2026-08-15 제거)를 대체 - videofeed.domain.VideoTicker와
// 구조적으로 동일하되, 이제 개별 글이 아니라 다이제스트(TelegramDigest)에
// 태깅된다.
@Entity
@Table(name = "telegram_digest_ticker", indexes = {
    @Index(name = "idx_telegram_digest_ticker_ticker_code", columnList = "ticker_code"),
    @Index(name = "idx_telegram_digest_ticker_digest", columnList = "telegram_digest_id")
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class TelegramDigestTicker extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "telegram_digest_ticker_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "telegram_digest_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private TelegramDigest telegramDigest;

    @Column(name = "ticker_code", nullable = false, length = 10)
    private String tickerCode;

    @Column(name = "ticker_name", length = 100)
    private String tickerName;

    @Column(name = "stance", length = 20)
    private String stance;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Builder
    private TelegramDigestTicker(TelegramDigest telegramDigest, String tickerCode, String tickerName,
                                  String stance, BigDecimal confidence) {
        validateTelegramDigestTicker(telegramDigest, tickerCode);
        this.telegramDigest = telegramDigest;
        this.tickerCode = tickerCode;
        this.tickerName = tickerName;
        this.stance = stance;
        this.confidence = confidence;
    }

    public static TelegramDigestTicker of(TelegramDigest telegramDigest, String tickerCode, String tickerName,
                                           String stance, BigDecimal confidence) {
        return TelegramDigestTicker.builder()
            .telegramDigest(telegramDigest)
            .tickerCode(tickerCode)
            .tickerName(tickerName)
            .stance(stance)
            .confidence(confidence)
            .build();
    }

    private void validateTelegramDigestTicker(TelegramDigest telegramDigest, String tickerCode) {
        Assert.notNull(telegramDigest, "다이제스트는 필수입니다.");
        Assert.hasText(tickerCode, "종목 코드는 필수입니다.");
    }
}
