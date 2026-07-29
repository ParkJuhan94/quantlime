package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SummarizeApiRequest(
    @JsonProperty("video_title") String videoTitle,
    @JsonProperty("channel_name") String channelName,
    @JsonProperty("transcript_content") String transcriptContent
) {
}
