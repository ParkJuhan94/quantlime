package com.quantlime.telegramfeed.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import com.quantlime.videofeed.domain.Channel;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static lombok.AccessLevel.PROTECTED;

/**
 * 채널×날짜 단위 다이제스트 요약(2026-08-15, TelegramSummary를 대체).
 * 애초 유튜브 Summary와 대응해 글(TelegramPost) 1:1로 설계했으나,
 * "미국 주식 인사이더" 채널이 하루 70건 이상 올라오는 걸 실측하고 나서
 * 글 단위 개별 요약 대신 그날 SELECTED된 글 전부를 합쳐 하나의 다이제스트로
 * 요약하는 쪽으로 재설계했다 - 개별 요약은 짧은 단신 하나하나를 태워
 * Gemini 쿼터를 소모하는 데 비해 정보 밀도가 낮고, 다이제스트는 "그날
 * 무슨 일이 있었는지"를 한 번에 보여줘 실사용성이 더 높다는 판단
 * (docs/ROADMAP.md "Phase 8 P7" 재검토 참고).
 */
@Entity
@Table(name = "telegram_digest",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_telegram_digest", columnNames = {"channel_id", "digest_date"})
    },
    // uk_telegram_digest는 (channel_id, digest_date) 복합이라 digest_date만
    // 단독으로 정렬하는 쿼리(TelegramDigestRepository.findDigests의
    // ORDER BY d.digestDate DESC)의 leftmost prefix로 못 쓴다. 성능 개선
    // 계획 문서 Phase 2 참고.
    indexes = @Index(name = "idx_telegram_digest_date", columnList = "digest_date DESC")
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class TelegramDigest extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "telegram_digest_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false, foreignKey = @ForeignKey(NO_CONSTRAINT))
    private Channel channel;

    // 다이제스트가 다루는 날짜(발행일 기준) - 이 채널의 그날 SELECTED된
    // TelegramPost 전체가 재료가 된다. 재료 목록은 별도 컬럼으로 저장하지
    // 않고 조회 시점에 TelegramPostRepository로 다시 조회한다(중복 저장
    // 방지 - 재료 글이 나중에 보존기간 정리로 지워지면 자연히 목록에서도
    // 빠짐).
    @Column(name = "digest_date", nullable = false)
    private LocalDate digestDate;

    @Column(name = "model", nullable = false, length = 50)
    private String model;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Column(name = "input_token")
    private Integer inputToken;

    @Column(name = "output_token")
    private Integer outputToken;

    @Builder
    private TelegramDigest(Channel channel, LocalDate digestDate, String model, String payload,
                            Integer inputToken, Integer outputToken) {
        validateTelegramDigest(channel, digestDate, model, payload);
        this.channel = channel;
        this.digestDate = digestDate;
        this.model = model;
        this.payload = payload;
        this.inputToken = inputToken;
        this.outputToken = outputToken;
    }

    public static TelegramDigest of(Channel channel, LocalDate digestDate, String model, String payload,
                                     Integer inputToken, Integer outputToken) {
        return TelegramDigest.builder()
            .channel(channel)
            .digestDate(digestDate)
            .model(model)
            .payload(payload)
            .inputToken(inputToken)
            .outputToken(outputToken)
            .build();
    }

    // 같은 날짜 다이제스트를 재생성(수집 사이클마다 누적 재요약)할 때
    // 새 행을 또 만들지 않고 기존 행을 덮어쓴다 - uk_telegram_digest
    // 유니크 제약과 맞물려 채널×날짜당 항상 최신 한 건만 존재한다.
    public void overwrite(String model, String payload, Integer inputToken, Integer outputToken) {
        this.model = model;
        this.payload = payload;
        this.inputToken = inputToken;
        this.outputToken = outputToken;
    }

    private void validateTelegramDigest(Channel channel, LocalDate digestDate, String model, String payload) {
        Assert.notNull(channel, "채널은 필수입니다.");
        Assert.notNull(digestDate, "다이제스트 날짜는 필수입니다.");
        Assert.hasText(model, "모델명은 필수입니다.");
        Assert.hasText(payload, "요약 페이로드는 필수입니다.");
    }
}
