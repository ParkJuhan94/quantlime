package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quantlime.infra.python.dto.BacktestApiRequest.OhlcvApiItem;
import java.util.List;

// (축, horizon) 하나당 1건씩 호출한다 - 500종목 규모에서 8개 조합을 한
// 호출에 몰아넣으면 PythonEngineClient read timeout(60초)을 넘긴다는 게
// 실측으로 확인돼(2026-08 감사 세션), quant-engine 쪽 CrossSectionalBacktestRequest도
// 동일하게 axis/horizon 단일 필드로 좁혀뒀다.
public record CrossSectionalBacktestApiRequest(
    String market,
    @JsonProperty("score_version") String scoreVersion,
    List<StockDailyScoreApiItem> stocks,
    @JsonProperty("benchmark_ohlcv") List<OhlcvApiItem> benchmarkOhlcv,
    String axis,
    int horizon,
    @JsonProperty("null_test") boolean nullTest,
    @JsonProperty("null_repeats") int nullRepeats
) {

    public record StockDailyScoreApiItem(
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("daily_scores") List<DailyScorePointApiItem> dailyScores
    ) {
    }

    // date를 String으로 두는 이유는 다른 python DTO들과 동일(PythonEngineConfig
    // 주석 참고 - RestClient 기본 컨버터가 LocalDate를 배열로 다루는 문제).
    public record DailyScorePointApiItem(
        String date,
        double close,
        @JsonProperty("trend_score") Double trendScore,
        @JsonProperty("mean_reversion_score") Double meanReversionScore
    ) {
    }
}
