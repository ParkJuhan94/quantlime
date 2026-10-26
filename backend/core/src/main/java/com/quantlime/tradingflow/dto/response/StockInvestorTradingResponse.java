package com.quantlime.tradingflow.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import org.springframework.lang.Nullable;

/**
 * 종목 일별 투자자 순매수 거래량(주). 개인·기타법인은 당일 잠정 기록에서 null이고 확정치가 반영되는 당일 저녁부터
 * 채워진다({@code confirmed}가 그 여부). 기관 세부 7개 분류도 확정 후에만 있다. 외국인 보유율은 소수 비율(0.5089 = 50.89%)이다.
 */
public record StockInvestorTradingResponse(
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul") LocalDate tradeDate,
    @Nullable Long individualNetBuyVolume,
    Long foreignerNetBuyVolume,
    Long institutionNetBuyVolume,
    @Nullable Long otherCorporationNetBuyVolume,
    @Nullable InstitutionBreakdownResponse institutionBreakdown,
    @Nullable Double foreignerHoldingRate,
    boolean confirmed
) {

    /** 기관 세부 7개 분류의 순매수 거래량(주). */
    public record InstitutionBreakdownResponse(
        Long financialInvestmentNetBuyVolume,
        Long insuranceNetBuyVolume,
        Long trustNetBuyVolume,
        Long privateEquityFundNetBuyVolume,
        Long bankNetBuyVolume,
        Long otherFinancialInstitutionNetBuyVolume,
        Long pensionFundNetBuyVolume
    ) {
    }
}
