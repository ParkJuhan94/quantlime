package com.quantlime.score.domain;

import com.quantlime.common.domain.TimeBaseEntity;
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
    indexes = {
        @Index(
            name = "idx_score_stock_date",
            columnList = "stock_code, score_date DESC"
        ),
        // /api/dashboard/scores(전체 랭킹) 쿼리가 튜플 IN 서브쿼리로
        // "종목별 최신 score_date"만 걸러낸 뒤 ORDER BY composite_score DESC
        // LIMIT 50을 도는데, 이 인덱스가 없으면 옵티마이저가 전체 후보를
        // 스캔한 뒤 정렬한다. 2026-08-19 실측: 이 인덱스 추가로 옵티마이저가
        // composite_score 인덱스를 내림차순으로 훑으며 조건을 만족하는 50개를
        // 찾는 즉시 멈추는 전략(EXPLAIN상 rows=50)으로 바뀌어 상관 서브쿼리
        // 대비 약 100배(116.7초→~1초 내외) 단축됐다(성능 개선 계획 문서 B1 참고).
        @Index(
            name = "idx_score_composite_score",
            columnList = "composite_score DESC"
        )
    }
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Score extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "score_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
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

    @Column(name = "insufficient_data", nullable = false)
    private boolean insufficientData;

    @Builder
    private Score(String stockCode, LocalDate scoreDate, Double trendScore,
                  Double meanReversionScore, Double compositeScore, Grade grade,
                  Quadrant quadrant, Divergence divergence,
                  boolean insufficientData) {
        validateScore(stockCode, scoreDate);
        this.stockCode = stockCode;
        this.scoreDate = scoreDate;
        this.trendScore = trendScore;
        this.meanReversionScore = meanReversionScore;
        this.compositeScore = compositeScore;
        this.grade = grade;
        this.quadrant = quadrant;
        this.divergence = divergence;
        this.insufficientData = insufficientData;
    }

    public static Score of(String stockCode, LocalDate scoreDate, Double trendScore,
                           Double meanReversionScore, Double compositeScore, Grade grade,
                           Quadrant quadrant, Divergence divergence,
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
                           Divergence divergence, boolean insufficientData) {
        this.trendScore = trendScore;
        this.meanReversionScore = meanReversionScore;
        this.compositeScore = compositeScore;
        this.grade = grade;
        this.quadrant = quadrant;
        this.divergence = divergence;
        this.insufficientData = insufficientData;
    }

    private void validateScore(String stockCode, LocalDate scoreDate) {
        Assert.hasText(stockCode, "종목 코드는 필수입니다.");
        Assert.notNull(scoreDate, "스코어 산출일은 필수입니다.");
    }
}
