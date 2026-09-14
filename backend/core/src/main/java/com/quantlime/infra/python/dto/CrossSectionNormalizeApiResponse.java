package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CrossSectionNormalizeApiResponse(
    @JsonProperty("as_of") String asOf,
    @JsonProperty("peer_group") String peerGroup,
    // 표본이 MIN_STOCKS_PER_DATE 미만이면 false이고 이때 모든 items의
    // percentile/grade는 null이다 - 호출측은 이 경우 저장을 건너뛰어야 한다.
    @JsonProperty("min_sample_met") boolean minSampleMet,
    List<NormalizedItemApiResponse> items
) {

    public record NormalizedItemApiResponse(
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("trend_percentile") Double trendPercentile,
        @JsonProperty("mean_reversion_percentile") Double meanReversionPercentile,
        @JsonProperty("composite_percentile") Double compositePercentile,
        String grade
    ) {
    }
}
