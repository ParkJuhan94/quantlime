package com.quantlime.infra.toss.dto;

import com.quantlime.price.domain.DailyPrice;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class TossPriceMapper {

    public static LocalDate toLocalDate(String isoTimestamp) {
        return OffsetDateTime.parse(isoTimestamp).toLocalDate();
    }

    public static DailyPrice toDailyPrice(
        String stockCode,
        TossCandleResponse.TossCandle candle) {
        return DailyPrice.of(
            stockCode,
            toLocalDate(candle.timestamp()),
            Long.parseLong(candle.openPrice()),
            Long.parseLong(candle.highPrice()),
            Long.parseLong(candle.lowPrice()),
            Long.parseLong(candle.closePrice()),
            Long.parseLong(candle.volume())
        );
    }
}
