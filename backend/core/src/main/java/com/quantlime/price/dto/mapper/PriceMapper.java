package com.quantlime.price.dto.mapper;

import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.dto.response.CurrentPriceResponse;
import com.quantlime.price.dto.response.DailyChartResponse;
import com.quantlime.price.dto.response.PriceSnapshot;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class PriceMapper {

    private static final String KRW = "KRW";

    // 캐시 미스(관심종목이 아니거나 스윕 스케줄러가 아직 한 틱도 안 돈
    // 경우) 경로는 Toss를 직접 호출하지 않고 DB에 이미 있는 마지막 종가로
    // 응답한다(StockPriceService 참고) - MarketPriceSweepScheduler의
    // 청크 페이싱과 무관하게 동작하던 무페이싱 직접 호출이 429 반복
    // 발생의 원인이었다(2026-07-17). 등락률은 호출 측이 PreviousCloseCache로
    // 조회해 넘겨준 전일종가와 비교해 계산한다.
    public static CurrentPriceResponse toCurrentPriceResponse(DailyPrice latestClose, Long previousClose) {
        Long price = latestClose.getClosePrice();
        return new CurrentPriceResponse(
            latestClose.getStockCode(), price, calculateChangeRate(price, previousClose),
            KRW, latestClose.getTradeDate().toString());
    }

    // 캐시(PriceCacheStore)에 저장된 실시간 스냅샷은 통화 정보를 들고 있지
    // 않다(국내 주식만 다루는 프로젝트 범위상 원화 고정) - 조회 API 응답
    // 형태를 그대로 맞추기 위해 KRW로 채운다.
    public static CurrentPriceResponse toCurrentPriceResponse(PriceSnapshot snapshot) {
        return new CurrentPriceResponse(
            snapshot.stockCode(), snapshot.currentPrice(), snapshot.changeRate(),
            KRW, snapshot.timestamp());
    }

    private static Double calculateChangeRate(Long currentPrice, Long previousClose) {
        if (currentPrice == null || previousClose == null || previousClose == 0) {
            return null;
        }
        return (currentPrice - previousClose) * 100.0 / previousClose;
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
