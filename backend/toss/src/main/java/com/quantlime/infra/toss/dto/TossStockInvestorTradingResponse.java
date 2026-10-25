package com.quantlime.infra.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * GET /api/v1/stocks/{symbol}/investor-trading 응답 - 국내(KR) 종목만 지원(다른 시장은 400
 * unsupported-market). 개인·외국인·기관·기타법인 일별 매수/매도/순매수 거래량(주, 정수 문자열)을 최신순으로
 * 주며 KRX·NXT 통합 기준이다. 시장지표 투자자별 매매대금({@link TossInvestorTradingResponse})과 달리
 * 금액이 아니라 거래량이고 외국인은 등록외국인만이다.
 *
 * <p>당일 기록은 장중 잠정치라 {@code individual}·{@code otherCorporation}·{@code institution.breakdown}·
 * {@code foreignerHolding}·{@code cfd}가 null이다. 확정치는 당일 저녁에 채워지고 외국인 보유는 다음 영업일
 * 오전에 한 번 더 갱신될 수 있어, 호출 측은 {@code updatedAt}을 저장해 바뀌면 덮어써야 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossStockInvestorTradingResponse(
    StockInvestorTradingResult result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockInvestorTradingResult(
        String nextUntil,
        List<StockInvestorTradingRecord> records
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockInvestorTradingRecord(
        String date,
        String updatedAt,
        InvestorTradingVolume individual,
        InvestorTradingVolume foreigner,
        InstitutionTradingVolume institution,
        InvestorTradingVolume otherCorporation,
        ForeignerHolding foreignerHolding
    ) {
    }

    // 기관 합계 + 세부 7개 분류. breakdown은 당일 잠정 기록에서 null이다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstitutionTradingVolume(
        String buyVolume,
        String sellVolume,
        String netBuyVolume,
        InstitutionBreakdown breakdown
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstitutionBreakdown(
        InvestorTradingVolume financialInvestment,
        InvestorTradingVolume insurance,
        InvestorTradingVolume trust,
        InvestorTradingVolume privateEquityFund,
        InvestorTradingVolume bank,
        InvestorTradingVolume otherFinancialInstitution,
        InvestorTradingVolume pensionFund
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvestorTradingVolume(
        String buyVolume,
        String sellVolume,
        String netBuyVolume
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForeignerHolding(
        String holdingQuantity,
        String limitQuantity,
        String holdingRate
    ) {
    }
}
