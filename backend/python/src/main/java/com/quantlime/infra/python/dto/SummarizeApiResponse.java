package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SummarizeApiResponse(
    String summary,
    @JsonProperty("key_points") List<String> keyPoints,
    @JsonProperty("macro_points") List<String> macroPoints,
    @JsonProperty("mentioned_tickers") List<TickerMentionApiResponse> mentionedTickers,
    String caveat,
    String model,
    @JsonProperty("input_tokens") int inputTokens,
    @JsonProperty("output_tokens") int outputTokens
) {

    public record TickerMentionApiResponse(
        @JsonProperty("ticker_code") String tickerCode,
        @JsonProperty("ticker_name") String tickerName,
        String stance,
        double confidence
    ) {
    }
}
