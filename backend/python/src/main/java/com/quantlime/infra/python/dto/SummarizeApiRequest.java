package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SummarizeApiRequest(
    @JsonProperty("video_title") String videoTitle,
    @JsonProperty("channel_name") String channelName,
    @JsonProperty("transcript_content") String transcriptContent,
    @JsonProperty("source_kind") String sourceKind
) {

    // 기존 유튜브 호출부(SummaryCollectionFacade)가 쓰던 3-arg 생성자 - source_kind
    // 없이도 그대로 컴파일되도록 유지하고, 기본값 "youtube"로 위임한다(Phase 8 P7-4,
    // quant-engine SummarizeRequest.source_kind 기본값과 동일).
    public SummarizeApiRequest(String videoTitle, String channelName, String transcriptContent) {
        this(videoTitle, channelName, transcriptContent, "youtube");
    }
}
