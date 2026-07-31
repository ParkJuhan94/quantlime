package com.quantlime.infra.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeChannelsResponse(
    List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, Snippet snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(Thumbnails thumbnails) {
    }

    // default(88x88)만 쓴다 - 채널 프로필 아바타로는 medium/high까지 필요 없다.
    // JSON 키가 "default"라 자바 예약어와 겹쳐 필드명은 defaultThumbnail로
    // 바꾸고 @JsonProperty로 실제 키에 매핑한다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Thumbnails(@JsonProperty("default") Thumbnail defaultThumbnail) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Thumbnail(String url) {
    }
}
