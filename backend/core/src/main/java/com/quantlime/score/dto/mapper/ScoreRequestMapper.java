package com.quantlime.score.dto.mapper;

import com.quantlime.infra.python.dto.ScoreBatchApiRequest;
import com.quantlime.infra.python.dto.ScoreBatchApiRequest.OhlcvApiItem;
import com.quantlime.infra.python.dto.ScoreBatchApiRequest.StockScoreApiRequest;
import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class ScoreRequestMapper {

    public static StockScoreApiRequest toStockScoreApiRequest(
        String stockCode, List<DailyPrice> dailyPrices) {
        List<OhlcvApiItem> ohlcv = dailyPrices.stream()
            .map(ScoreRequestMapper::toOhlcvApiItem)
            .toList();
        return new StockScoreApiRequest(stockCode, ohlcv);
    }

    public static ScoreBatchApiRequest toScoreBatchApiRequest(
        Map<String, List<DailyPrice>> dailyPricesByStockCode) {
        List<StockScoreApiRequest> stocks = dailyPricesByStockCode.entrySet().stream()
            .map(entry -> toStockScoreApiRequest(entry.getKey(), entry.getValue()))
            .toList();
        return new ScoreBatchApiRequest(stocks);
    }

    // 해외종목은 OHLCV가 overseas_daily_price(Double 가격 컬럼)에 저장돼
    // DailyPrice와 타입이 달라 별도 메서드명으로 오버로드한다(같은 시그니처로
    // 오버로드하면 List<DailyPrice>/List<OverseasDailyPrice>가 제네릭 소거 후
    // 동일해져 컴파일 에러가 난다).
    public static StockScoreApiRequest toOverseasStockScoreApiRequest(
        String stockCode, List<OverseasDailyPrice> overseasDailyPrices) {
        List<OhlcvApiItem> ohlcv = overseasDailyPrices.stream()
            .map(ScoreRequestMapper::toOhlcvApiItem)
            .toList();
        return new StockScoreApiRequest(stockCode, ohlcv);
    }

    public static ScoreBatchApiRequest toOverseasScoreBatchApiRequest(
        Map<String, List<OverseasDailyPrice>> overseasDailyPricesByStockCode) {
        List<StockScoreApiRequest> stocks = overseasDailyPricesByStockCode.entrySet().stream()
            .map(entry -> toOverseasStockScoreApiRequest(entry.getKey(), entry.getValue()))
            .toList();
        return new ScoreBatchApiRequest(stocks);
    }

    private static OhlcvApiItem toOhlcvApiItem(DailyPrice dailyPrice) {
        return new OhlcvApiItem(
            dailyPrice.getTradeDate().toString(),
            dailyPrice.getOpenPrice(),
            dailyPrice.getHighPrice(),
            dailyPrice.getLowPrice(),
            dailyPrice.getClosePrice(),
            dailyPrice.getVolume()
        );
    }

    private static OhlcvApiItem toOhlcvApiItem(OverseasDailyPrice overseasDailyPrice) {
        return new OhlcvApiItem(
            overseasDailyPrice.getTradeDate().toString(),
            overseasDailyPrice.getOpenPrice(),
            overseasDailyPrice.getHighPrice(),
            overseasDailyPrice.getLowPrice(),
            overseasDailyPrice.getClosePrice(),
            overseasDailyPrice.getVolume()
        );
    }
}
