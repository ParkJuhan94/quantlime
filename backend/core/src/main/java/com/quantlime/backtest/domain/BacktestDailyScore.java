package com.quantlime.backtest.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import com.quantlime.score.domain.Grade;
import com.quantlime.score.domain.Quadrant;
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

/**
 * 백테스트 기간 전체의 일별 스코어 원시 시계열 - 백테스트 요약 통계
 * ({@link BacktestResult})와 별개로, 프론트 백테스트 페이지의 가격차트
 * 오버레이·사분면 배경밴드·스코어 분포 히스토그램(Phase F)에 쓰인다.
 *
 * <p>{@code Grade}/{@code Quadrant}는 score 도메인에 이미 있는 enum을 그대로
 * 재사용한다 - 퀀트 엔진이 내려주는 코드가 완전히 동일해(scorer.py) 별도
 * enum을 새로 만드는 건 중복이다.
 */
@Entity
@Table(name = "backtest_daily_score",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_backtest_daily_score_stock_version_date",
        columnNames = {"stock_code", "score_version", "trade_date"}
    ),
    indexes = @Index(
        name = "idx_backtest_daily_score_stock_version",
        columnList = "stock_code, score_version, trade_date"
    )
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class BacktestDailyScore extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "backtest_daily_score_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 6)
    private String stockCode;

    @Column(name = "score_version", nullable = false, length = 20)
    private String scoreVersion;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "close_price", nullable = false)
    private Double closePrice;

    @Column(name = "trend_score")
    private Double trendScore;

    @Column(name = "mean_reversion_score")
    private Double meanReversionScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "quadrant", columnDefinition = "varchar(30)")
    private Quadrant quadrant;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", columnDefinition = "varchar(20)")
    private Grade grade;

    @Builder
    private BacktestDailyScore(String stockCode, String scoreVersion, LocalDate tradeDate,
                               Double closePrice, Double trendScore, Double meanReversionScore,
                               Quadrant quadrant, Grade grade) {
        Assert.hasText(stockCode, "종목 코드는 필수입니다.");
        Assert.hasText(scoreVersion, "스코어 버전은 필수입니다.");
        Assert.notNull(tradeDate, "거래일은 필수입니다.");
        Assert.notNull(closePrice, "종가는 필수입니다.");
        this.stockCode = stockCode;
        this.scoreVersion = scoreVersion;
        this.tradeDate = tradeDate;
        this.closePrice = closePrice;
        this.trendScore = trendScore;
        this.meanReversionScore = meanReversionScore;
        this.quadrant = quadrant;
        this.grade = grade;
    }

    public static BacktestDailyScore of(String stockCode, String scoreVersion, LocalDate tradeDate,
                                        Double closePrice, Double trendScore, Double meanReversionScore,
                                        Quadrant quadrant, Grade grade) {
        return BacktestDailyScore.builder()
            .stockCode(stockCode)
            .scoreVersion(scoreVersion)
            .tradeDate(tradeDate)
            .closePrice(closePrice)
            .trendScore(trendScore)
            .meanReversionScore(meanReversionScore)
            .quadrant(quadrant)
            .grade(grade)
            .build();
    }
}
