package com.quantlab.price.dto.mapper;

import com.quantlab.infra.toss.dto.TossPriceResponse;
import com.quantlab.price.domain.DailyPrice;
import com.quantlab.price.dto.response.CurrentPriceResponse;
import com.quantlab.price.dto.response.DailyChartResponse;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class PriceMapper {

    public static CurrentPriceResponse toCurrentPriceResponse(
        String stockCode, TossPriceResponse.TossPrice tossPrice) {
        Long price = StringUtils.hasText(tossPrice.lastPrice())
            ? Long.parseLong(tossPrice.lastPrice()) : null;
        return new CurrentPriceResponse(
            stockCode, price, tossPrice.currency(), tossPrice.timestamp());
    }

    public static DailyChartResponse toDailyChartResponse(DailyPrice dailyPrice) {
        return new DailyChartResponse(
            dailyPrice.getTradeDate(),
            dailyPrice.getOpenPrice(),
            dailyPrice.getHighPrice(),
            dailyPrice.getLowPrice(),
            dailyPrice.getClosePrice(),
            dailyPrice.getVolume()
        );
    }
}
