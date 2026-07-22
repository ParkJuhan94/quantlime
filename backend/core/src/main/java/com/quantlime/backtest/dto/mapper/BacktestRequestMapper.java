package com.quantlime.backtest.dto.mapper;

import com.quantlime.infra.python.dto.BacktestApiRequest;
import com.quantlime.infra.python.dto.BacktestApiRequest.OhlcvApiItem;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.price.domain.DailyPrice;
import java.util.List;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class BacktestRequestMapper {

    public static BacktestApiRequest toBacktestApiRequest(
        String stockCode, List<DailyPrice> dailyPrices, List<BenchmarkIndex> benchmarkPrices) {
        return new BacktestApiRequest(
            stockCode,
            dailyPrices.stream().map(BacktestRequestMapper::toOhlcvApiItem).toList(),
            benchmarkPrices.stream().map(BacktestRequestMapper::toOhlcvApiItem).toList()
        );
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

    // 벤치마크 지수는 거래량이 없어(BenchmarkIndex 엔티티 자체에 volume 컬럼
    // 없음) 0으로 채운다 - 백테스트 통계는 벤치마크의 종가만 사용하고
    // 거래량은 쓰지 않는다(calculator/backtest.py 참고).
    private static OhlcvApiItem toOhlcvApiItem(BenchmarkIndex benchmarkIndex) {
        return new OhlcvApiItem(
            benchmarkIndex.getTradeDate().toString(),
            benchmarkIndex.getOpenPrice(),
            benchmarkIndex.getHighPrice(),
            benchmarkIndex.getLowPrice(),
            benchmarkIndex.getClosePrice(),
            0.0
        );
    }
}
