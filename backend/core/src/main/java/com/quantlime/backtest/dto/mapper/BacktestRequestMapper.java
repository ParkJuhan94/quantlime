package com.quantlime.backtest.dto.mapper;

import com.quantlime.infra.python.dto.BacktestApiRequest;
import com.quantlime.infra.python.dto.BacktestApiRequest.OhlcvApiItem;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import java.util.List;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class BacktestRequestMapper {

    public static BacktestApiRequest toBacktestApiRequest(
        String stockCode, List<DomesticDailyPrice> domesticDailyPrices, List<BenchmarkIndex> benchmarkPrices) {
        return new BacktestApiRequest(
            stockCode,
            domesticDailyPrices.stream().map(BacktestRequestMapper::toOhlcvApiItem).toList(),
            benchmarkPrices.stream().map(BacktestRequestMapper::toOhlcvApiItem).toList()
        );
    }

    // 해외종목은 OverseasDailyPrice(Double 가격 컬럼)를 쓰므로 ScoreRequestMapper와
    // 동일한 이유로 별도 메서드명 오버로드로 둔다.
    public static BacktestApiRequest toOverseasBacktestApiRequest(
        String stockCode, List<OverseasDailyPrice> overseasDailyPrices, List<BenchmarkIndex> benchmarkPrices) {
        return new BacktestApiRequest(
            stockCode,
            overseasDailyPrices.stream().map(BacktestRequestMapper::toOhlcvApiItem).toList(),
            benchmarkPrices.stream().map(BacktestRequestMapper::toOhlcvApiItem).toList()
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

    private static OhlcvApiItem toOhlcvApiItem(DomesticDailyPrice domesticDailyPrice) {
        return new OhlcvApiItem(
            domesticDailyPrice.getTradeDate().toString(),
            domesticDailyPrice.getOpenPrice(),
            domesticDailyPrice.getHighPrice(),
            domesticDailyPrice.getLowPrice(),
            domesticDailyPrice.getClosePrice(),
            domesticDailyPrice.getVolume()
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
