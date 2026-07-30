package com.quantlime.market.dto.response;

import java.time.LocalDate;

/**
 * 투자자별 순매수(매수-매도, KRW) - 개인/외국인/기관계 + 기관 세부 7종/
 * 기타법인. 사용자가 요청한 건 "순매수"라 매수/매도 원본 금액은 굳이
 * 응답에 넣지 않는다(필요해지면 그때 필드를 추가).
 */
public record InvestorTradingResponse(
    LocalDate baseDate,
    Long individualNetBuyAmount,
    Long foreignerNetBuyAmount,
    Long institutionNetBuyAmount,
    Long financialInvestmentNetBuyAmount,
    Long insuranceNetBuyAmount,
    Long trustNetBuyAmount,
    Long privateEquityFundNetBuyAmount,
    Long bankNetBuyAmount,
    Long otherFinancialInstitutionNetBuyAmount,
    Long pensionFundNetBuyAmount,
    Long otherCorporationNetBuyAmount
) {
}
