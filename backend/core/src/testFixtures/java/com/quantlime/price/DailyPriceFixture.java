package com.quantlime.price;

import com.quantlime.price.domain.DailyPrice;
import java.time.LocalDate;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class DailyPriceFixture {

    public static DailyPrice createDailyPrice(String stockCode, LocalDate tradeDate) {
        return DailyPrice.of(stockCode, tradeDate, 100L, 110L, 90L, 105L, 1000L);
    }
}
