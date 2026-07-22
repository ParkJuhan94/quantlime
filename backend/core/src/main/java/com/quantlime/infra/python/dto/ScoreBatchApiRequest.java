package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ScoreBatchApiRequest(
    List<StockScoreApiRequest> stocks
) {

    public record StockScoreApiRequest(
        @JsonProperty("stock_code") String stockCode,
        List<OhlcvApiItem> ohlcv
    ) {
    }

    // date는 의도적으로 LocalDate가 아닌 String("yyyy-MM-dd")이다. RestClient의
    // 기본 Jackson 컨버터가 LocalDate를 [2026,7,1] 같은 배열로 직렬화해버려
    // 문자열을 기대하는 Python(pydantic) 파싱이 깨졌고, LocalDate 필드에
    // @JsonFormat을 붙이는 시도도 (원인 미상으로) 요청 바디 자체가 완전히
    // 비어버리는 문제를 일으켰다. LocalDate.toString()이 이미 ISO-8601
    // "yyyy-MM-dd"를 반환하므로, 매퍼에서 문자열로 변환해 넘기는 편이
    // 가장 단순하고 확실하다.
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
