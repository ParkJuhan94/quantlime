package com.quantlab.price.dto.mapper;

import com.quantlab.infra.toss.dto.TossPriceResponse;
import com.quantlab.price.domain.DailyPrice;
import com.quantlab.price.dto.response.CurrentPriceResponse;
import com.quantlab.price.dto.response.DailyChartResponse;
import com.quantlab.price.dto.response.PriceSnapshot;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class PriceMapper {

    private static final String KRW = "KRW";

    public static CurrentPriceResponse toCurrentPriceResponse(
        String stockCode, TossPriceResponse.TossPrice tossPrice) {
        Long price = StringUtils.hasText(tossPrice.lastPrice())
            ? Long.parseLong(tossPrice.lastPrice()) : null;
        return new CurrentPriceResponse(
            stockCode, price, tossPrice.currency(), tossPrice.timestamp());
    }

    // 캐시(PriceCacheStore)에 저장된 실시간 스냅샷은 통화 정보를 들고 있지
    // 않다(국내 주식만 다루는 프로젝트 범위상 원화 고정) - 조회 API 응답
    // 형태를 그대로 맞추기 위해 KRW로 채운다.
    public static CurrentPriceResponse toCurrentPriceResponse(PriceSnapshot snapshot) {
        return new CurrentPriceResponse(
            snapshot.stockCode(), snapshot.currentPrice(), KRW, snapshot.timestamp());
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
