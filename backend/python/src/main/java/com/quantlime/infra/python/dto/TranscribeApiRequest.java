package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TranscribeApiRequest(
    @JsonProperty("video_id") String videoId
) {
}
