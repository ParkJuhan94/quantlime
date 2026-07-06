package com.quantlab.score.dto.mapper;

import com.quantlab.infra.python.dto.ScoreBatchApiRequest;
import com.quantlab.infra.python.dto.ScoreBatchApiRequest.OhlcvApiItem;
import com.quantlab.infra.python.dto.ScoreBatchApiRequest.StockScoreApiRequest;
import com.quantlab.price.domain.DailyPrice;
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
}
