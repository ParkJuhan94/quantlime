package com.quantlime.price;

import com.quantlime.price.domain.OverseasDailyPrice;
import java.time.LocalDate;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class OverseasDailyPriceFixture {

    public static OverseasDailyPrice createDailyPrice(String stockCode, LocalDate tradeDate) {
        return OverseasDailyPrice.of(stockCode, tradeDate, 100.5, 110.5, 90.5, 105.5, 1000L);
    }
}
