package com.quantlime.infra.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeVideosResponse(
    List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, ContentDetails contentDetails, Statistics statistics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentDetails(String duration) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Statistics(String viewCount) {
    }
}
