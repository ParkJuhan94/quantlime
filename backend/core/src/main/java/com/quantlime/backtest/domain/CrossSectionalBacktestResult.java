package com.quantlime.backtest.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import com.quantlime.stock.domain.MarketType;
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
 * 시장 하나(코스피/코스닥/나스닥/뉴욕)의 (축, horizon) 조합에 대한 횡단면
 * Rank IC 결과 - 같은 날짜에 여러 종목을 줄세워 비교하는 방식이라, 종목
 * 하나의 시간축 안에서만 상관을 재는 {@link BacktestResult}와는 답하는
 * 질문 자체가 다르다(2026-08 감사 세션에서 둘의 결과가 반대 부호로 나오는
 * 걸 발견해 이 엔티티를 새로 도입했다 - 상세는 docs/CHANGELOG.md 참고).
 * 종목 단위가 아니라 시장 단위 통계라 키에 stock_code가 없다.
 */
@Entity
@Table(name = "backtest_cross_sectional_result",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_cross_sectional_result_market_axis_horizon_version_split",
        columnNames = {"market_type", "axis", "horizon_days", "score_version", "sample_split"}
    )
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class CrossSectionalBacktestResult extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "backtest_cross_sectional_result_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", nullable = false, length = 20)
    private MarketType marketType;

    @Enumerated(EnumType.STRING)
    @Column(name = "axis", nullable = false, length = 20)
    private BacktestAxis axis;

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;

    @Column(name = "score_version", nullable = false, length = 20)
    private String scoreVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "sample_split", nullable = false, length = 20)
    private BacktestSampleSplit sampleSplit;

    @Column(name = "backtest_date", nullable = false)
    private LocalDate backtestDate;

    @Column(name = "stock_count", nullable = false)
    private int stockCount;

    @Column(name = "mean_ic")
    private Double meanIc;

    @Column(name = "ic_ci_low")
    private Double icCiLow;

    @Column(name = "ic_ci_high")
    private Double icCiHigh;

    @Column(name = "sample_dates", nullable = false)
    private int sampleDates;

    @Column(name = "sample_observations", nullable = false)
    private int sampleObservations;

    // 순환이동 널 테스트(nullTest=true 호출 시에만 채워짐) - 관측 IC가 이
    // "우연/기계적 편향만으로 나올 수 있는" 분포 밖에 있는지 판단하는 근거.
    @Column(name = "null_mean")
    private Double nullMean;

    @Column(name = "null_std")
    private Double nullStd;

    @Column(name = "null_percentile_low")
    private Double nullPercentileLow;

    @Column(name = "null_percentile_high")
    private Double nullPercentileHigh;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "backtest_cross_sectional_bucket",
        joinColumns = @JoinColumn(name = "backtest_cross_sectional_result_id"),
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private List<BacktestBucket> buckets = new ArrayList<>();

    @Builder
    private CrossSectionalBacktestResult(MarketType marketType, BacktestAxis axis, int horizonDays,
                                         String scoreVersion, BacktestSampleSplit sampleSplit,
                                         LocalDate backtestDate, int stockCount,
                                         Double meanIc, Double icCiLow, Double icCiHigh,
                                         int sampleDates, int sampleObservations,
                                         Double nullMean, Double nullStd,
                                         Double nullPercentileLow, Double nullPercentileHigh,
                                         List<BacktestBucket> buckets) {
        validate(marketType, axis, scoreVersion, sampleSplit, backtestDate);
        this.marketType = marketType;
        this.axis = axis;
        this.horizonDays = horizonDays;
        this.scoreVersion = scoreVersion;
        this.sampleSplit = sampleSplit;
        this.backtestDate = backtestDate;
        this.stockCount = stockCount;
        this.meanIc = meanIc;
        this.icCiLow = icCiLow;
        this.icCiHigh = icCiHigh;
        this.sampleDates = sampleDates;
        this.sampleObservations = sampleObservations;
        this.nullMean = nullMean;
        this.nullStd = nullStd;
        this.nullPercentileLow = nullPercentileLow;
        this.nullPercentileHigh = nullPercentileHigh;
        this.buckets = buckets != null ? new ArrayList<>(buckets) : new ArrayList<>();
    }

    public static CrossSectionalBacktestResult of(MarketType marketType, BacktestAxis axis, int horizonDays,
                                                  String scoreVersion, BacktestSampleSplit sampleSplit,
                                                  LocalDate backtestDate, int stockCount,
                                                  Double meanIc, Double icCiLow, Double icCiHigh,
                                                  int sampleDates, int sampleObservations,
                                                  Double nullMean, Double nullStd,
                                                  Double nullPercentileLow, Double nullPercentileHigh,
                                                  List<BacktestBucket> buckets) {
        return CrossSectionalBacktestResult.builder()
            .marketType(marketType)
            .axis(axis)
            .horizonDays(horizonDays)
            .scoreVersion(scoreVersion)
            .sampleSplit(sampleSplit)
            .backtestDate(backtestDate)
            .stockCount(stockCount)
            .meanIc(meanIc)
            .icCiLow(icCiLow)
            .icCiHigh(icCiHigh)
            .sampleDates(sampleDates)
            .sampleObservations(sampleObservations)
            .nullMean(nullMean)
            .nullStd(nullStd)
            .nullPercentileLow(nullPercentileLow)
            .nullPercentileHigh(nullPercentileHigh)
            .buckets(buckets)
            .build();
    }

    /** 같은 (시장, 축, horizon, 버전, 표본분할)로 재실행되면 새 행 대신 갱신한다. */
    public void updateFrom(LocalDate backtestDate, int stockCount,
                           Double meanIc, Double icCiLow, Double icCiHigh,
                           int sampleDates, int sampleObservations,
                           Double nullMean, Double nullStd,
                           Double nullPercentileLow, Double nullPercentileHigh,
                           List<BacktestBucket> buckets) {
        this.backtestDate = backtestDate;
        this.stockCount = stockCount;
        this.meanIc = meanIc;
        this.icCiLow = icCiLow;
        this.icCiHigh = icCiHigh;
        this.sampleDates = sampleDates;
        this.sampleObservations = sampleObservations;
        this.nullMean = nullMean;
        this.nullStd = nullStd;
        this.nullPercentileLow = nullPercentileLow;
        this.nullPercentileHigh = nullPercentileHigh;
        this.buckets.clear();
        if (buckets != null) {
            this.buckets.addAll(buckets);
        }
    }

    private void validate(MarketType marketType, BacktestAxis axis, String scoreVersion,
                          BacktestSampleSplit sampleSplit, LocalDate backtestDate) {
        Assert.notNull(marketType, "시장 구분은 필수입니다.");
        Assert.notNull(axis, "백테스트 축은 필수입니다.");
        Assert.hasText(scoreVersion, "스코어 버전은 필수입니다.");
        Assert.notNull(sampleSplit, "표본분할 구분은 필수입니다.");
        Assert.notNull(backtestDate, "백테스트 실행일은 필수입니다.");
    }
}
