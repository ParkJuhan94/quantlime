package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 같은 날짜·같은 모집단(국내 또는 해외)으로 이미 걸러진 절대 서브스코어
 * 리스트를 quant-engine에 넘겨 횡단면 백분위를 받는다. OHLCV는 포함하지
 * 않는다 - quant-engine calculator/normalization.py 모듈 docstring 참고.
 */
public record CrossSectionNormalizeApiRequest(
    @JsonProperty("as_of") String asOf,
    @JsonProperty("peer_group") String peerGroup,
    List<AxisScoreApiItem> items
) {

    public record AxisScoreApiItem(
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("trend_score") Double trendScore,
        @JsonProperty("mean_reversion_score") Double meanReversionScore
    ) {
    }
}
