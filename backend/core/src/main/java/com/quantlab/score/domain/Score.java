package com.quantlab.score.domain;

import com.quantlab.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "score",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_score_stock_date",
        columnNames = {"stock_code", "score_date"}
    ),
    indexes = @Index(
        name = "idx_score_stock_date",
        columnList = "stock_code, score_date DESC"
    )
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Score extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "score_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 6)
    private String stockCode;

    @Column(name = "score_date", nullable = false)
    private LocalDate scoreDate;

    @Column(name = "trend_score")
    private Double trendScore;

    @Column(name = "mean_reversion_score")
    private Double meanReversionScore;

    @Column(name = "composite_score")
    private Double compositeScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", length = 10)
    private Grade grade;

    @Column(name = "divergence_flag")
    private Boolean divergenceFlag;

    @Column(name = "divergence_message", length = 200)
    private String divergenceMessage;

    @Column(name = "comment", nullable = false, length = 500)
    private String comment;

    @Column(name = "insufficient_data", nullable = false)
    private boolean insufficientData;

    @Builder
    private Score(String stockCode, LocalDate scoreDate, Double trendScore,
                  Double meanReversionScore, Double compositeScore, Grade grade,
                  Boolean divergenceFlag, String divergenceMessage,
                  String comment, boolean insufficientData) {
        validateScore(stockCode, scoreDate, comment);
        this.stockCode = stockCode;
        this.scoreDate = scoreDate;
        this.trendScore = trendScore;
        this.meanReversionScore = meanReversionScore;
        this.compositeScore = compositeScore;
        this.grade = grade;
        this.divergenceFlag = divergenceFlag;
        this.divergenceMessage = divergenceMessage;
        this.comment = comment;
        this.insufficientData = insufficientData;
    }

    public static Score of(String stockCode, LocalDate scoreDate, Double trendScore,
                           Double meanReversionScore, Double compositeScore, Grade grade,
                           Boolean divergenceFlag, String divergenceMessage,
                           String comment, boolean insufficientData) {
        return Score.builder()
            .stockCode(stockCode)
            .scoreDate(scoreDate)
            .trendScore(trendScore)
            .meanReversionScore(meanReversionScore)
            .compositeScore(compositeScore)
            .grade(grade)
            .divergenceFlag(divergenceFlag)
            .divergenceMessage(divergenceMessage)
            .comment(comment)
            .insufficientData(insufficientData)
            .build();
    }

    /**
     * 같은 날 재계산이 발생했을 때(예: 관심 종목 등록 직후 + 이후 수동 재계산이
     * 겹치는 경우) 새 행을 또 만들지 않고 기존 당일 행의 값을 갱신한다.
     * setter를 두지 않는 컨벤션을 지키기 위한 비즈니스 메서드.
     */
    public void updateFrom(Double trendScore, Double meanReversionScore,
                           Double compositeScore, Grade grade,
                           Boolean divergenceFlag, String divergenceMessage,
                           String comment, boolean insufficientData) {
        Assert.hasText(comment, "코멘트는 필수입니다.");
        this.trendScore = trendScore;
        this.meanReversionScore = meanReversionScore;
        this.compositeScore = compositeScore;
        this.grade = grade;
        this.divergenceFlag = divergenceFlag;
        this.divergenceMessage = divergenceMessage;
        this.comment = comment;
        this.insufficientData = insufficientData;
    }

    private void validateScore(String stockCode, LocalDate scoreDate, String comment) {
        Assert.hasText(stockCode, "종목 코드는 필수입니다.");
        Assert.notNull(scoreDate, "스코어 산출일은 필수입니다.");
        Assert.hasText(comment, "코멘트는 필수입니다.");
    }
}
