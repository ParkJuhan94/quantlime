package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quantlime.infra.python.dto.BacktestApiResponse.BucketStatApiResponse;
import java.util.List;

public record CrossSectionalBacktestApiResponse(
    String market,
    @JsonProperty("score_version") String scoreVersion,
    @JsonProperty("stock_count") int stockCount,
    String axis,
    int horizon,
    @JsonProperty("mean_ic") Double meanIc,
    @JsonProperty("ic_ci_low") Double icCiLow,
    @JsonProperty("ic_ci_high") Double icCiHigh,
    @JsonProperty("n_dates") int nDates,
    @JsonProperty("n_observations") int nObservations,
    List<BucketStatApiResponse> buckets,
    @JsonProperty("null_mean") Double nullMean,
    @JsonProperty("null_std") Double nullStd,
    @JsonProperty("null_p2_5") Double nullP2_5,
    @JsonProperty("null_p97_5") Double nullP97_5
) {
}
