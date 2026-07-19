package com.quantlab.score.domain;

import com.quantlab.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

    // Hibernate가 @Enumerated(STRING)을 MySQL 네이티브 enum(...) 컬럼으로
    // 매핑해와서, Grade 상수 이름이 바뀔 때마다(SSS~D → 5단계) 기존 행에 남은
    // 옛 값이 새 enum 값 목록에 없어 ALTER TABLE 자체가 "Data truncated"로
    // 실패했다(레거시 값이 남아있는 한 매 기동마다 반복 재현됨) - columnDefinition을
    // 명시해 항상 varchar로 고정한다. enum→varchar 전환은 값 목록 제약이
    // 없어 레거시 데이터가 남아 있어도 안전하게 끝난다.
    @Enumerated(EnumType.STRING)
    @Column(name = "grade", columnDefinition = "varchar(20)")
    private Grade grade;

    // Grade와 동일한 이유로(§58-63 주석 참고) columnDefinition을 varchar로
    // 고정한다 - 사분면 라벨(Quadrant) 자체가 새로 추가된 값이라 지금은
    // 레거시 데이터 문제가 없지만, 향후 상수 이름이 바뀔 가능성에 대비해
    // 처음부터 동일 패턴으로 통일한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "quadrant", columnDefinition = "varchar(30)")
    private Quadrant quadrant;

    @Embedded
    private Divergence divergence;

    @Column(name = "comment", nullable = false, length = 500)
    private String comment;

    @Column(name = "insufficient_data", nullable = false)
    private boolean insufficientData;

    @Builder
    private Score(String stockCode, LocalDate scoreDate, Double trendScore,
                  Double meanReversionScore, Double compositeScore, Grade grade,
                  Quadrant quadrant, Divergence divergence, String comment,
                  boolean insufficientData) {
        validateScore(stockCode, scoreDate, comment);
        this.stockCode = stockCode;
        this.scoreDate = scoreDate;
        this.trendScore = trendScore;
        this.meanReversionScore = meanReversionScore;
        this.compositeScore = compositeScore;
        this.grade = grade;
        this.quadrant = quadrant;
        this.divergence = divergence;
        this.comment = comment;
        this.insufficientData = insufficientData;
    }

    public static Score of(String stockCode, LocalDate scoreDate, Double trendScore,
                           Double meanReversionScore, Double compositeScore, Grade grade,
                           Quadrant quadrant, Divergence divergence, String comment,
                           boolean insufficientData) {
        return Score.builder()
            .stockCode(stockCode)
            .scoreDate(scoreDate)
            .trendScore(trendScore)
            .meanReversionScore(meanReversionScore)
            .compositeScore(compositeScore)
            .grade(grade)
            .quadrant(quadrant)
            .divergence(divergence)
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
                           Double compositeScore, Grade grade, Quadrant quadrant,
                           Divergence divergence, String comment, boolean insufficientData) {
        Assert.hasText(comment, "코멘트는 필수입니다.");
        this.trendScore = trendScore;
        this.meanReversionScore = meanReversionScore;
        this.compositeScore = compositeScore;
        this.grade = grade;
        this.quadrant = quadrant;
        this.divergence = divergence;
        this.comment = comment;
        this.insufficientData = insufficientData;
    }

    private void validateScore(String stockCode, LocalDate scoreDate, String comment) {
        Assert.hasText(stockCode, "종목 코드는 필수입니다.");
        Assert.notNull(scoreDate, "스코어 산출일은 필수입니다.");
        Assert.hasText(comment, "코멘트는 필수입니다.");
    }
}
