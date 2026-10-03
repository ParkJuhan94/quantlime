package com.quantlime.infra.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubePlaylistItemsResponse(
    String nextPageToken,
    List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Snippet snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(String title, String publishedAt, ResourceId resourceId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResourceId(String videoId) {
    }
}
