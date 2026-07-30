package com.quantlime.market.domain;

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
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static lombok.AccessLevel.PROTECTED;

/**
 * 코스피/코스닥 투자자별(개인/외국인/기관/기타법인) 매매대금 - Toss
 * market-indicators/investor-trading 응답을 주간/월간 단위로 영속 저장한다
 * (2026-07-29 신규, 사용자 요청 범위가 주/월뿐이라 일별은 저장하지 않음).
 * 금액 22개는 {@link InvestorTradingAmounts}(Embeddable)로 묶어 같은 타입
 * 파라미터가 줄줄이 이어지는 문제를 피했다.
 *
 * <p>당일/당주/당월 기록은 장 종료 전까지 갱신되는 잠정치라, {@code
 * sourceUpdatedAt}(Toss updatedAt)이 바뀌면 기존 행을 덮어써야 한다 -
 * {@code baseDate} 존재 여부만으로 skip하면 잠정치가 영원히 남는다
 * (InvestorTradingBackfillService 참고).
 */
@Entity
@Table(name = "investor_trading",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_investor_trading_market_interval_date",
        columnNames = {"market_code", "aggregation_interval", "base_date"}
    ),
    indexes = @Index(
        name = "idx_investor_trading_market_interval_date",
        columnList = "market_code, aggregation_interval, base_date DESC"
    )
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class InvestorTrading extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "investor_trading_id")
    private Long id;

    @Column(name = "market_code", nullable = false, length = 20)
    private String marketCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregation_interval", nullable = false, length = 20)
    private AggregationInterval aggregationInterval;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Column(name = "source_updated_at", nullable = false)
    private LocalDateTime sourceUpdatedAt;

    @Embedded
    private InvestorTradingAmounts amounts;

    @Builder
    private InvestorTrading(String marketCode, AggregationInterval aggregationInterval,
                            LocalDate baseDate, LocalDateTime sourceUpdatedAt,
                            InvestorTradingAmounts amounts) {
        validateInvestorTrading(marketCode, aggregationInterval, baseDate, sourceUpdatedAt, amounts);
        this.marketCode = marketCode;
        this.aggregationInterval = aggregationInterval;
        this.baseDate = baseDate;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.amounts = amounts;
    }

    public static InvestorTrading of(String marketCode, AggregationInterval aggregationInterval,
                                     LocalDate baseDate, LocalDateTime sourceUpdatedAt,
                                     InvestorTradingAmounts amounts) {
        return InvestorTrading.builder()
            .marketCode(marketCode)
            .aggregationInterval(aggregationInterval)
            .baseDate(baseDate)
            .sourceUpdatedAt(sourceUpdatedAt)
            .amounts(amounts)
            .build();
    }

    /**
     * 잠정치 갱신(당일/당주/당월) 시 기존 행을 덮어쓴다 - 정체성 필드
     * (marketCode/aggregationInterval/baseDate)는 바뀌지 않으므로 금액과
     * sourceUpdatedAt만 갱신한다.
     */
    public void updateAmounts(LocalDateTime sourceUpdatedAt, InvestorTradingAmounts amounts) {
        Assert.notNull(sourceUpdatedAt, "원본 갱신시각은 필수입니다.");
        Assert.notNull(amounts, "매매대금은 필수입니다.");
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.amounts = amounts;
    }

    private void validateInvestorTrading(String marketCode, AggregationInterval aggregationInterval,
                                         LocalDate baseDate, LocalDateTime sourceUpdatedAt,
                                         InvestorTradingAmounts amounts) {
        Assert.hasText(marketCode, "시장 코드는 필수입니다.");
        Assert.notNull(aggregationInterval, "집계 단위는 필수입니다.");
        Assert.notNull(baseDate, "기준일은 필수입니다.");
        Assert.notNull(sourceUpdatedAt, "원본 갱신시각은 필수입니다.");
        Assert.notNull(amounts, "매매대금은 필수입니다.");
    }
}
