package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TranscribeApiResponse(
    boolean available,
    String source,
    String lang,
    String content,
    @JsonProperty("char_count") Integer charCount,
    String reason
) {
}
