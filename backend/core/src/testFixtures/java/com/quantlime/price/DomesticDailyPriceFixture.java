package com.quantlime.price;

import com.quantlime.price.domain.DomesticDailyPrice;
import java.time.LocalDate;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class DomesticDailyPriceFixture {

    public static DomesticDailyPrice createDailyPrice(String stockCode, LocalDate tradeDate) {
        return DomesticDailyPrice.of(stockCode, tradeDate, 100L, 110L, 90L, 105L, 1000L);
    }
}
