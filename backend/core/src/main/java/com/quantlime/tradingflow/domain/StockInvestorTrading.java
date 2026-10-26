package com.quantlime.tradingflow.domain;

import static lombok.AccessLevel.PROTECTED;

import com.quantlime.common.domain.TimeBaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 국내 종목의 일별 투자자(개인/외국인/기관/기타법인) 매매동향 - 토스 {@code stocks/{symbol}/investor-trading}
 * 응답을 종목×거래일 1행으로 영속 저장한다(2026-10-26). 지수 단위 {@code InvestorTrading}(거래대금, 주/월 집계)과 달리
 * 종목 단위 일별 거래량(주)이며 KRX·NXT 통합 기준이다. 외국인은 등록외국인만이다.
 *
 * <p>당일 기록은 장중 잠정치라 개인·기타법인·외국인 보유현황이 null이고, 확정치는 당일 저녁에 채워진다. 그래서
 * {@code sourceUpdatedAt}(토스 updatedAt)이 바뀌면 기존 행을 덮어써야 하며({@link #updateFrom}), 개인이 채워졌는지
 * 여부로 확정 여부를 판단한다({@link #isConfirmed}). 외국인·기관은 잠정치에도 항상 있다.
 */
@Entity
@Table(name = "stock_investor_trading",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_stock_investor_trading_stock_date",
        columnNames = {"stock_code", "trade_date"}
    )
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class StockInvestorTrading extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_investor_trading_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "source_updated_at", nullable = false)
    private LocalDateTime sourceUpdatedAt;

    @Nullable
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "buyVolume", column = @Column(name = "individual_buy_volume")),
        @AttributeOverride(name = "sellVolume", column = @Column(name = "individual_sell_volume")),
        @AttributeOverride(name = "netBuyVolume", column = @Column(name = "individual_net_buy_volume"))
    })
    private InvestorVolume individual;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "buyVolume", column = @Column(name = "foreigner_buy_volume")),
        @AttributeOverride(name = "sellVolume", column = @Column(name = "foreigner_sell_volume")),
        @AttributeOverride(name = "netBuyVolume", column = @Column(name = "foreigner_net_buy_volume"))
    })
    private InvestorVolume foreigner;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "buyVolume", column = @Column(name = "institution_buy_volume")),
        @AttributeOverride(name = "sellVolume", column = @Column(name = "institution_sell_volume")),
        @AttributeOverride(name = "netBuyVolume", column = @Column(name = "institution_net_buy_volume"))
    })
    private InvestorVolume institution;

    // 기관 세부 7개 분류의 순매수 - 잠정 기록에는 없어 null이다(InstitutionBreakdown 참고).
    @Nullable
    @Embedded
    private InstitutionBreakdown institutionBreakdown;

    @Nullable
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "buyVolume", column = @Column(name = "other_corporation_buy_volume")),
        @AttributeOverride(name = "sellVolume", column = @Column(name = "other_corporation_sell_volume")),
        @AttributeOverride(name = "netBuyVolume", column = @Column(name = "other_corporation_net_buy_volume"))
    })
    private InvestorVolume otherCorporation;

    @Nullable
    @Column(name = "foreigner_holding_quantity")
    private Long foreignerHoldingQuantity;

    // 상장주식수 대비 외국인 보유 비율(소수 비율, 0.5089 = 50.89%).
    @Nullable
    @Column(name = "foreigner_holding_rate")
    private Double foreignerHoldingRate;

    @Builder
    private StockInvestorTrading(String stockCode, LocalDate tradeDate, LocalDateTime sourceUpdatedAt,
                                 @Nullable InvestorVolume individual, InvestorVolume foreigner,
                                 InvestorVolume institution, @Nullable InstitutionBreakdown institutionBreakdown,
                                 @Nullable InvestorVolume otherCorporation,
                                 @Nullable Long foreignerHoldingQuantity, @Nullable Double foreignerHoldingRate) {
        validate(stockCode, tradeDate, sourceUpdatedAt, foreigner, institution);
        this.stockCode = stockCode;
        this.tradeDate = tradeDate;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.individual = individual;
        this.foreigner = foreigner;
        this.institution = institution;
        this.institutionBreakdown = institutionBreakdown;
        this.otherCorporation = otherCorporation;
        this.foreignerHoldingQuantity = foreignerHoldingQuantity;
        this.foreignerHoldingRate = foreignerHoldingRate;
    }

    public static StockInvestorTrading of(String stockCode, LocalDate tradeDate, LocalDateTime sourceUpdatedAt,
                                          @Nullable InvestorVolume individual, InvestorVolume foreigner,
                                          InvestorVolume institution,
                                          @Nullable InstitutionBreakdown institutionBreakdown,
                                          @Nullable InvestorVolume otherCorporation,
                                          @Nullable Long foreignerHoldingQuantity,
                                          @Nullable Double foreignerHoldingRate) {
        return StockInvestorTrading.builder()
            .stockCode(stockCode)
            .tradeDate(tradeDate)
            .sourceUpdatedAt(sourceUpdatedAt)
            .individual(individual)
            .foreigner(foreigner)
            .institution(institution)
            .institutionBreakdown(institutionBreakdown)
            .otherCorporation(otherCorporation)
            .foreignerHoldingQuantity(foreignerHoldingQuantity)
            .foreignerHoldingRate(foreignerHoldingRate)
            .build();
    }

    /**
     * 잠정치가 확정치로 바뀌거나 외국인 보유가 갱신될 때 기존 행을 덮어쓴다 - 정체성 필드(stockCode/tradeDate)는
     * 바뀌지 않는다.
     */
    public void updateFrom(LocalDateTime sourceUpdatedAt, @Nullable InvestorVolume individual,
                           InvestorVolume foreigner, InvestorVolume institution,
                           @Nullable InstitutionBreakdown institutionBreakdown,
                           @Nullable InvestorVolume otherCorporation, @Nullable Long foreignerHoldingQuantity,
                           @Nullable Double foreignerHoldingRate) {
        Assert.notNull(sourceUpdatedAt, "원본 갱신시각은 필수입니다.");
        Assert.notNull(foreigner, "외국인 매매동향은 필수입니다.");
        Assert.notNull(institution, "기관 매매동향은 필수입니다.");
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.individual = individual;
        this.foreigner = foreigner;
        this.institution = institution;
        this.institutionBreakdown = institutionBreakdown;
        this.otherCorporation = otherCorporation;
        this.foreignerHoldingQuantity = foreignerHoldingQuantity;
        this.foreignerHoldingRate = foreignerHoldingRate;
    }

    /**
     * 기관 세부 컬럼이 추가되기 전에 저장된 행처럼 세부가 비어 있는지 - 토스 응답에 세부가 있으면 {@code
     * sourceUpdatedAt}이 같아도 덮어써서 채워야 한다(이력 재백필).
     */
    public boolean lacksInstitutionBreakdown() {
        return institutionBreakdown == null;
    }

    /** 개인 매매동향이 채워졌으면 확정치다(당일 잠정 기록에는 개인이 null). */
    public boolean isConfirmed() {
        return individual != null;
    }

    private void validate(String stockCode, LocalDate tradeDate, LocalDateTime sourceUpdatedAt,
                          InvestorVolume foreigner, InvestorVolume institution) {
        Assert.hasText(stockCode, "종목 코드는 필수입니다.");
        Assert.notNull(tradeDate, "거래일은 필수입니다.");
        Assert.notNull(sourceUpdatedAt, "원본 갱신시각은 필수입니다.");
        Assert.notNull(foreigner, "외국인 매매동향은 필수입니다.");
        Assert.notNull(institution, "기관 매매동향은 필수입니다.");
    }
}
