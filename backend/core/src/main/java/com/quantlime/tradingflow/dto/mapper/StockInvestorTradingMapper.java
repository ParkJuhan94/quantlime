package com.quantlime.tradingflow.dto.mapper;

import static lombok.AccessLevel.PRIVATE;

import com.quantlime.tradingflow.domain.InstitutionBreakdown;
import com.quantlime.tradingflow.domain.InvestorVolume;
import com.quantlime.tradingflow.domain.StockInvestorTrading;
import com.quantlime.tradingflow.dto.response.StockInvestorTradingResponse;
import com.quantlime.tradingflow.dto.response.StockInvestorTradingResponse.InstitutionBreakdownResponse;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

@NoArgsConstructor(access = PRIVATE)
public final class StockInvestorTradingMapper {

    public static StockInvestorTradingResponse toStockInvestorTradingResponse(StockInvestorTrading trading) {
        return new StockInvestorTradingResponse(
            trading.getTradeDate(),
            netBuyOf(trading.getIndividual()),
            trading.getForeigner().getNetBuyVolume(),
            trading.getInstitution().getNetBuyVolume(),
            netBuyOf(trading.getOtherCorporation()),
            toBreakdownResponse(trading.getInstitutionBreakdown()),
            trading.getForeignerHoldingRate(),
            trading.isConfirmed());
    }

    @Nullable
    private static InstitutionBreakdownResponse toBreakdownResponse(@Nullable InstitutionBreakdown breakdown) {
        if (breakdown == null) {
            return null;
        }
        return new InstitutionBreakdownResponse(
            breakdown.getFinancialInvestmentNetBuyVolume(),
            breakdown.getInsuranceNetBuyVolume(),
            breakdown.getTrustNetBuyVolume(),
            breakdown.getPrivateEquityFundNetBuyVolume(),
            breakdown.getBankNetBuyVolume(),
            breakdown.getOtherFinancialInstitutionNetBuyVolume(),
            breakdown.getPensionFundNetBuyVolume());
    }

    @Nullable
    private static Long netBuyOf(@Nullable InvestorVolume volume) {
        return volume == null ? null : volume.getNetBuyVolume();
    }
}
