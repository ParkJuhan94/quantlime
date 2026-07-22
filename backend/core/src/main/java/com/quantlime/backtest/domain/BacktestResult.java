package com.quantlime.backtest.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static lombok.AccessLevel.PROTECTED;

/**
 * 종목 하나의 (축, horizon) 조합에 대한 백테스트 결과 - Rank IC(+신뢰구간),
 * 분위수 버킷 5개, 스코어 안정성(자기상관/등급 플립률)을 담는다.
 *
 * <p>스코어 안정성(scoreAutocorrelation/gradeFlipRate)은 axis 단위 지표라
 * 같은 축의 4개 horizon 행에 동일 값이 중복 저장된다 - 별도 3번째 엔티티로
 * 쪼개는 정규화보다, 이 정도 중복(축당 2개 double 값 x 4배)을 감수하고 조회
 * 시 조인 하나를 줄이는 편을 택했다(읽기 위주의 v1 리포팅 데이터라는 점
 * 고려).
 */
@Entity
@Table(name = "backtest_result",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_backtest_result_stock_axis_horizon_version",
        columnNames = {"stock_code", "axis", "horizon_days", "score_version"}
    ),
    indexes = @Index(
        name = "idx_backtest_result_stock_version",
        columnList = "stock_code, score_version"
    )
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class BacktestResult extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "backtest_result_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 6)
    private String stockCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "axis", nullable = false, length = 20)
    private BacktestAxis axis;

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;

    @Column(name = "score_version", nullable = false, length = 20)
    private String scoreVersion;

    @Column(name = "backtest_date", nullable = false)
    private LocalDate backtestDate;

    @Column(name = "sample_size", nullable = false)
    private int sampleSize;

    @Column(name = "rank_ic")
    private Double rankIc;

    @Column(name = "rank_ic_ci_low")
    private Double rankIcCiLow;

    @Column(name = "rank_ic_ci_high")
    private Double rankIcCiHigh;

    @Column(name = "score_autocorrelation")
    private Double scoreAutocorrelation;

    @Column(name = "grade_flip_rate")
    private Double gradeFlipRate;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "backtest_bucket",
        joinColumns = @JoinColumn(name = "backtest_result_id"),
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private List<BacktestBucket> buckets = new ArrayList<>();

    @Builder
    private BacktestResult(String stockCode, BacktestAxis axis, int horizonDays,
                           String scoreVersion, LocalDate backtestDate, int sampleSize,
                           Double rankIc, Double rankIcCiLow, Double rankIcCiHigh,
                           Double scoreAutocorrelation, Double gradeFlipRate,
                           List<BacktestBucket> buckets) {
        validateBacktestResult(stockCode, axis, scoreVersion, backtestDate);
        this.stockCode = stockCode;
        this.axis = axis;
        this.horizonDays = horizonDays;
        this.scoreVersion = scoreVersion;
        this.backtestDate = backtestDate;
        this.sampleSize = sampleSize;
        this.rankIc = rankIc;
        this.rankIcCiLow = rankIcCiLow;
        this.rankIcCiHigh = rankIcCiHigh;
        this.scoreAutocorrelation = scoreAutocorrelation;
        this.gradeFlipRate = gradeFlipRate;
        this.buckets = buckets != null ? new ArrayList<>(buckets) : new ArrayList<>();
    }

    public static BacktestResult of(String stockCode, BacktestAxis axis, int horizonDays,
                                    String scoreVersion, LocalDate backtestDate, int sampleSize,
                                    Double rankIc, Double rankIcCiLow, Double rankIcCiHigh,
                                    Double scoreAutocorrelation, Double gradeFlipRate,
                                    List<BacktestBucket> buckets) {
        return BacktestResult.builder()
            .stockCode(stockCode)
            .axis(axis)
            .horizonDays(horizonDays)
            .scoreVersion(scoreVersion)
            .backtestDate(backtestDate)
            .sampleSize(sampleSize)
            .rankIc(rankIc)
            .rankIcCiLow(rankIcCiLow)
            .rankIcCiHigh(rankIcCiHigh)
            .scoreAutocorrelation(scoreAutocorrelation)
            .gradeFlipRate(gradeFlipRate)
            .buckets(buckets)
            .build();
    }

    /**
     * 같은 종목·축·horizon·버전으로 재실행됐을 때(예: 같은 날 재트리거) 새
     * 행을 또 만들지 않고 기존 행을 갱신한다 - Score.updateFrom과 동일한
     * 이유(setter를 두지 않는 컨벤션 유지).
     */
    public void updateFrom(LocalDate backtestDate, int sampleSize, Double rankIc,
                           Double rankIcCiLow, Double rankIcCiHigh, Double scoreAutocorrelation,
                           Double gradeFlipRate, List<BacktestBucket> buckets) {
        this.backtestDate = backtestDate;
        this.sampleSize = sampleSize;
        this.rankIc = rankIc;
        this.rankIcCiLow = rankIcCiLow;
        this.rankIcCiHigh = rankIcCiHigh;
        this.scoreAutocorrelation = scoreAutocorrelation;
        this.gradeFlipRate = gradeFlipRate;
        this.buckets.clear();
        if (buckets != null) {
            this.buckets.addAll(buckets);
        }
    }

    private void validateBacktestResult(
        String stockCode, BacktestAxis axis, String scoreVersion, LocalDate backtestDate) {
        Assert.hasText(stockCode, "종목 코드는 필수입니다.");
        Assert.notNull(axis, "백테스트 축은 필수입니다.");
        Assert.hasText(scoreVersion, "스코어 버전은 필수입니다.");
        Assert.notNull(backtestDate, "백테스트 실행일은 필수입니다.");
    }
}
