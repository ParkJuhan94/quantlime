package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BacktestApiRequest(
    @JsonProperty("stock_code") String stockCode,
    List<OhlcvApiItem> ohlcv,
    @JsonProperty("benchmark_ohlcv") List<OhlcvApiItem> benchmarkOhlcv
) {

    // date를 String으로 두는 이유는 ScoreBatchApiRequest.OhlcvApiItem과 동일
    // (RestClient 기본 Jackson 컨버터가 LocalDate를 배열로 직렬화해 Python
    // 파싱이 깨졌던 이력 - PythonEngineConfig 주석 참고).
    public record OhlcvApiItem(
        String date,
        double open,
        double high,
        double low,
        double close,
        double volume
    ) {
    }
}
