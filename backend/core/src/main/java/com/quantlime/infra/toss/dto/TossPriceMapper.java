package com.quantlime.infra.toss.dto;

import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
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

    /**
     * 해외 종목은 달러 소수점 가격이라(예: $185.70) 국내와 달리 Long이 아닌
     * Double로 파싱한다({@link OverseasDailyPrice} 참고).
     */
    public static OverseasDailyPrice toOverseasDailyPrice(
        String stockCode,
        TossCandleResponse.TossCandle candle) {
        return OverseasDailyPrice.of(
            stockCode,
            toLocalDate(candle.timestamp()),
            Double.parseDouble(candle.openPrice()),
            Double.parseDouble(candle.highPrice()),
            Double.parseDouble(candle.lowPrice()),
            Double.parseDouble(candle.closePrice()),
            Long.parseLong(candle.volume())
        );
    }
}
