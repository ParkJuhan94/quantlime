package com.quantlime.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * 투자자별 매매대금(개인·외국인·기관계·기타법인 4주체 + 기관 세부 7종)의
 * 매수/매도 금액 22개를 묶은 값 객체 - {@link InvestorTrading}이 22개의
 * 개별 Long 파라미터를 순서대로 받으면 호출부에서 순서를 헷갈리기 쉬워
 * (전부 같은 타입) 이름 있는 필드로 감쌌다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = PROTECTED)
public class InvestorTradingAmounts {

    @Column(name = "individual_buy_amount", nullable = false)
    private Long individualBuyAmount;
    @Column(name = "individual_sell_amount", nullable = false)
    private Long individualSellAmount;

    @Column(name = "foreigner_buy_amount", nullable = false)
    private Long foreignerBuyAmount;
    @Column(name = "foreigner_sell_amount", nullable = false)
    private Long foreignerSellAmount;

    @Column(name = "institution_buy_amount", nullable = false)
    private Long institutionBuyAmount;
    @Column(name = "institution_sell_amount", nullable = false)
    private Long institutionSellAmount;

    @Column(name = "financial_investment_buy_amount", nullable = false)
    private Long financialInvestmentBuyAmount;
    @Column(name = "financial_investment_sell_amount", nullable = false)
    private Long financialInvestmentSellAmount;

    @Column(name = "insurance_buy_amount", nullable = false)
    private Long insuranceBuyAmount;
    @Column(name = "insurance_sell_amount", nullable = false)
    private Long insuranceSellAmount;

    @Column(name = "trust_buy_amount", nullable = false)
    private Long trustBuyAmount;
    @Column(name = "trust_sell_amount", nullable = false)
    private Long trustSellAmount;

    @Column(name = "private_equity_fund_buy_amount", nullable = false)
    private Long privateEquityFundBuyAmount;
    @Column(name = "private_equity_fund_sell_amount", nullable = false)
    private Long privateEquityFundSellAmount;

    @Column(name = "bank_buy_amount", nullable = false)
    private Long bankBuyAmount;
    @Column(name = "bank_sell_amount", nullable = false)
    private Long bankSellAmount;

    @Column(name = "other_financial_institution_buy_amount", nullable = false)
    private Long otherFinancialInstitutionBuyAmount;
    @Column(name = "other_financial_institution_sell_amount", nullable = false)
    private Long otherFinancialInstitutionSellAmount;

    @Column(name = "pension_fund_buy_amount", nullable = false)
    private Long pensionFundBuyAmount;
    @Column(name = "pension_fund_sell_amount", nullable = false)
    private Long pensionFundSellAmount;

    @Column(name = "other_corporation_buy_amount", nullable = false)
    private Long otherCorporationBuyAmount;
    @Column(name = "other_corporation_sell_amount", nullable = false)
    private Long otherCorporationSellAmount;

    @Builder
    private InvestorTradingAmounts(
        Long individualBuyAmount, Long individualSellAmount,
        Long foreignerBuyAmount, Long foreignerSellAmount,
        Long institutionBuyAmount, Long institutionSellAmount,
        Long financialInvestmentBuyAmount, Long financialInvestmentSellAmount,
        Long insuranceBuyAmount, Long insuranceSellAmount,
        Long trustBuyAmount, Long trustSellAmount,
        Long privateEquityFundBuyAmount, Long privateEquityFundSellAmount,
        Long bankBuyAmount, Long bankSellAmount,
        Long otherFinancialInstitutionBuyAmount, Long otherFinancialInstitutionSellAmount,
        Long pensionFundBuyAmount, Long pensionFundSellAmount,
        Long otherCorporationBuyAmount, Long otherCorporationSellAmount) {
        this.individualBuyAmount = individualBuyAmount;
        this.individualSellAmount = individualSellAmount;
        this.foreignerBuyAmount = foreignerBuyAmount;
        this.foreignerSellAmount = foreignerSellAmount;
        this.institutionBuyAmount = institutionBuyAmount;
        this.institutionSellAmount = institutionSellAmount;
        this.financialInvestmentBuyAmount = financialInvestmentBuyAmount;
        this.financialInvestmentSellAmount = financialInvestmentSellAmount;
        this.insuranceBuyAmount = insuranceBuyAmount;
        this.insuranceSellAmount = insuranceSellAmount;
        this.trustBuyAmount = trustBuyAmount;
        this.trustSellAmount = trustSellAmount;
        this.privateEquityFundBuyAmount = privateEquityFundBuyAmount;
        this.privateEquityFundSellAmount = privateEquityFundSellAmount;
        this.bankBuyAmount = bankBuyAmount;
        this.bankSellAmount = bankSellAmount;
        this.otherFinancialInstitutionBuyAmount = otherFinancialInstitutionBuyAmount;
        this.otherFinancialInstitutionSellAmount = otherFinancialInstitutionSellAmount;
        this.pensionFundBuyAmount = pensionFundBuyAmount;
        this.pensionFundSellAmount = pensionFundSellAmount;
        this.otherCorporationBuyAmount = otherCorporationBuyAmount;
        this.otherCorporationSellAmount = otherCorporationSellAmount;
    }
}
