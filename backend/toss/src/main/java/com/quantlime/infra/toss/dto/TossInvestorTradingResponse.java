package com.quantlime.infra.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * GET /api/v1/market-indicators/{symbol}/investor-trading 응답 - KOSPI/
 * KOSDAQ만 지원(그 외 심볼은 400 unsupported-symbol). 개인·외국인·기관·
 * 기타법인 4개 투자자 분류의 매수/매도 거래대금(KRW 정수)을 집계 단위
 * (interval)별로 최신순 제공하며, 기관은 7개 세부 분류(breakdown)를 함께
 * 준다. {@code institution.buyAmount}/{@code sellAmount}는 breakdown 7개
 * 항목의 합과 일치한다. 당일 기록은 장 종료 전까지 갱신될 수 있는
 * 잠정치라 {@code updatedAt}으로 마지막 갱신 시각을 확인해야 한다(호출
 * 측이 이 값을 저장해두고 바뀌면 덮어써야 함 - InvestorTradingBackfillService 참고).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossInvestorTradingResponse(
    InvestorTradingResult result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvestorTradingResult(
        String nextUntil,
        List<InvestorTradingRecord> records
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvestorTradingRecord(
        String date,
        String updatedAt,
        InvestorTradingAmount individual,
        InvestorTradingAmount foreigner,
        InstitutionTradingAmount institution,
        InvestorTradingAmount otherCorporation
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvestorTradingAmount(
        String buyAmount,
        String sellAmount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstitutionTradingAmount(
        String buyAmount,
        String sellAmount,
        InstitutionBreakdown breakdown
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstitutionBreakdown(
        InvestorTradingAmount financialInvestment,
        InvestorTradingAmount insurance,
        InvestorTradingAmount trust,
        InvestorTradingAmount privateEquityFund,
        InvestorTradingAmount bank,
        InvestorTradingAmount otherFinancialInstitution,
        InvestorTradingAmount pensionFund
    ) {
    }
}
