package com.quantlime.videofeed.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// Summary.payload(JSON 문자열)를 역직렬화하기 위한 내부 전용 DTO -
// SummaryPersistService.toPayloadJson()이 쓴 키(summary/key_points/
// macro_points/mentioned_tickers/caveat)와 맞춰야 한다. mentioned_tickers는
// 이미 VideoTicker 테이블에 정규화 저장돼 있어 조회 시엔 여기서 다시 쓰지
// 않으므로 알 수 없는 프로퍼티로 무시한다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SummaryPayload(
    String summary,
    @JsonProperty("key_points") List<String> keyPoints,
    @JsonProperty("macro_points") List<String> macroPoints,
    String caveat
) {
}
