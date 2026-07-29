package com.quantlime.price.dto.mapper;

import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.dto.response.CurrentPriceResponse;
import com.quantlime.price.dto.response.DailyChartResponse;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.util.ChangeRateCalculator;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class PriceMapper {

    private static final String KRW = "KRW";
    private static final String USD = "USD";

    // 캐시 미스(관심종목이 아니거나 스윕 스케줄러가 아직 한 틱도 안 돈
    // 경우) 경로는 Toss를 직접 호출하지 않고 DB에 이미 있는 마지막 종가로
    // 응답한다(StockPriceService 참고) - MarketPriceSweepScheduler의
    // 청크 페이싱과 무관하게 동작하던 무페이싱 직접 호출이 429 반복
    // 발생의 원인이었다(2026-07-17). 등락률은 호출 측이 PreviousCloseCache로
    // 조회해 넘겨준 전일종가와 비교해 계산한다.
    public static CurrentPriceResponse toCurrentPriceResponse(DailyPrice latestClose, Long previousClose) {
        Long price = latestClose.getClosePrice();
        Double changeRate = ChangeRateCalculator.calculate(
            price == null ? null : price.doubleValue(),
            previousClose == null ? null : previousClose.doubleValue());
        return new CurrentPriceResponse(
            latestClose.getStockCode(), price == null ? null : price.doubleValue(), changeRate,
            KRW, latestClose.getTradeDate().toString());
    }

    // 해외 종목의 캐시 미스 폴백(StockPriceService 참고) - 국내와 동일한
    // "DB의 마지막 확정 종가로 응답, Toss는 다시 호출 안 함" 원칙이지만
    // OverseasDailyPrice는 가격이 Double(USD)이라 별도 오버로드로 둔다.
    public static CurrentPriceResponse toCurrentPriceResponse(OverseasDailyPrice latestClose, Double previousClose) {
        Double price = latestClose.getClosePrice();
        Double changeRate = ChangeRateCalculator.calculate(price, previousClose);
        return new CurrentPriceResponse(
            latestClose.getStockCode(), price, changeRate,
            USD, latestClose.getTradeDate().toString());
    }

    // 캐시(PriceCacheStore)에 저장된 실시간 스냅샷은 통화 정보를 들고 있지
    // 않으므로(PriceSnapshot 참고) 호출 측이 종목의 시장 구분으로 판별한
    // 통화를 넘겨준다.
    public static CurrentPriceResponse toCurrentPriceResponse(PriceSnapshot snapshot, String currency) {
        return new CurrentPriceResponse(
            snapshot.stockCode(), snapshot.currentPrice(), snapshot.changeRate(),
            currency, snapshot.timestamp());
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
