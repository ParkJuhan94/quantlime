package com.quantlime.price;

import static lombok.AccessLevel.PRIVATE;

import com.quantlime.price.domain.OverseasDailyPrice;
import java.time.LocalDate;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class OverseasDailyPriceFixture {

    public static OverseasDailyPrice createDailyPrice(String stockCode, LocalDate tradeDate) {
        return OverseasDailyPrice.of(stockCode, tradeDate, 100.5, 110.5, 90.5, 105.5, 1000L);
    }
}
