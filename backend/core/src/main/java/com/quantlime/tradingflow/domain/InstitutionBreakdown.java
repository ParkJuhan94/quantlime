package com.quantlime.tradingflow.domain;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기관 합계를 이루는 세부 7개 분류(금융투자·보험·투신·사모·은행·기타금융·연기금)의 하루 순매수 거래량(주). 토스는 세부
 * 분류도 매수/매도/순매수를 주지만 화면과 수급 점수에는 순매수만 필요해 순매수만 저장한다(2026-10-26 결정, 필요해지면
 * 컬럼 추가). 당일 장중 잠정 기록에는 세부가 없어 {@link StockInvestorTrading}이 이 객체 자체를 null로 둔다 - 컬럼이
 * 전부 null이면 Hibernate가 임베디드 객체를 null로 읽는다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = PROTECTED)
public class InstitutionBreakdown {

    @Column(name = "institution_financial_investment_net_buy_volume")
    private Long financialInvestmentNetBuyVolume;

    @Column(name = "institution_insurance_net_buy_volume")
    private Long insuranceNetBuyVolume;

    @Column(name = "institution_trust_net_buy_volume")
    private Long trustNetBuyVolume;

    @Column(name = "institution_private_equity_fund_net_buy_volume")
    private Long privateEquityFundNetBuyVolume;

    @Column(name = "institution_bank_net_buy_volume")
    private Long bankNetBuyVolume;

    @Column(name = "institution_other_financial_institution_net_buy_volume")
    private Long otherFinancialInstitutionNetBuyVolume;

    @Column(name = "institution_pension_fund_net_buy_volume")
    private Long pensionFundNetBuyVolume;

    @Builder
    private InstitutionBreakdown(Long financialInvestmentNetBuyVolume, Long insuranceNetBuyVolume,
                                 Long trustNetBuyVolume, Long privateEquityFundNetBuyVolume,
                                 Long bankNetBuyVolume, Long otherFinancialInstitutionNetBuyVolume,
                                 Long pensionFundNetBuyVolume) {
        this.financialInvestmentNetBuyVolume = financialInvestmentNetBuyVolume;
        this.insuranceNetBuyVolume = insuranceNetBuyVolume;
        this.trustNetBuyVolume = trustNetBuyVolume;
        this.privateEquityFundNetBuyVolume = privateEquityFundNetBuyVolume;
        this.bankNetBuyVolume = bankNetBuyVolume;
        this.otherFinancialInstitutionNetBuyVolume = otherFinancialInstitutionNetBuyVolume;
        this.pensionFundNetBuyVolume = pensionFundNetBuyVolume;
    }
}
