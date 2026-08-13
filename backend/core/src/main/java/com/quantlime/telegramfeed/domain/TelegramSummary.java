package com.quantlime.telegramfeed.domain;

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

// videofeed.domain.Summary와 구조적으로 동일(payload json 키도 summary/
// key_points/macro_points/mentioned_tickers/caveat로 동일) - telegramfeed가
// videofeed를 단방향으로만 참조하는 패키지 경계를 지키기 위해 videofeed.dto의
// SummaryPayload를 재사용하지 않고 이쪽에 별도 payload record를 둔다(P7-4).
@Entity
@Table(name = "telegram_summary", uniqueConstraints = {
    @UniqueConstraint(name = "uk_telegram_summary", columnNames = {"telegram_post_id"})
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class TelegramSummary extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "telegram_summary_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "telegram_post_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private TelegramPost telegramPost;

    @Column(name = "model", nullable = false, length = 50)
    private String model;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Column(name = "input_token")
    private Integer inputToken;

    @Column(name = "output_token")
    private Integer outputToken;

    @Builder
    private TelegramSummary(TelegramPost telegramPost, String model, String payload,
                             Integer inputToken, Integer outputToken) {
        validateTelegramSummary(telegramPost, model, payload);
        this.telegramPost = telegramPost;
        this.model = model;
        this.payload = payload;
        this.inputToken = inputToken;
        this.outputToken = outputToken;
    }

    public static TelegramSummary of(TelegramPost telegramPost, String model, String payload,
                                      Integer inputToken, Integer outputToken) {
        return TelegramSummary.builder()
            .telegramPost(telegramPost)
            .model(model)
            .payload(payload)
            .inputToken(inputToken)
            .outputToken(outputToken)
            .build();
    }

    private void validateTelegramSummary(TelegramPost telegramPost, String model, String payload) {
        Assert.notNull(telegramPost, "텔레그램 글은 필수입니다.");
        Assert.hasText(model, "모델명은 필수입니다.");
        Assert.hasText(payload, "요약 페이로드는 필수입니다.");
    }
}
