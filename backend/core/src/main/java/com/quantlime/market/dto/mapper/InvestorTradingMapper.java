package com.quantlime.market.dto.mapper;

import com.quantlime.market.domain.InvestorTrading;
import com.quantlime.market.domain.InvestorTradingAmounts;
import com.quantlime.market.dto.response.InvestorTradingResponse;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class InvestorTradingMapper {

    public static InvestorTradingResponse toInvestorTradingResponse(InvestorTrading entity) {
        InvestorTradingAmounts amounts = entity.getAmounts();
        return new InvestorTradingResponse(
            entity.getBaseDate(),
            netBuy(amounts.getIndividualBuyAmount(), amounts.getIndividualSellAmount()),
            netBuy(amounts.getForeignerBuyAmount(), amounts.getForeignerSellAmount()),
            netBuy(amounts.getInstitutionBuyAmount(), amounts.getInstitutionSellAmount()),
            netBuy(amounts.getFinancialInvestmentBuyAmount(), amounts.getFinancialInvestmentSellAmount()),
            netBuy(amounts.getInsuranceBuyAmount(), amounts.getInsuranceSellAmount()),
            netBuy(amounts.getTrustBuyAmount(), amounts.getTrustSellAmount()),
            netBuy(amounts.getPrivateEquityFundBuyAmount(), amounts.getPrivateEquityFundSellAmount()),
            netBuy(amounts.getBankBuyAmount(), amounts.getBankSellAmount()),
            netBuy(amounts.getOtherFinancialInstitutionBuyAmount(), amounts.getOtherFinancialInstitutionSellAmount()),
            netBuy(amounts.getPensionFundBuyAmount(), amounts.getPensionFundSellAmount()),
            netBuy(amounts.getOtherCorporationBuyAmount(), amounts.getOtherCorporationSellAmount())
        );
    }

    private static long netBuy(Long buyAmount, Long sellAmount) {
        return buyAmount - sellAmount;
    }
}
