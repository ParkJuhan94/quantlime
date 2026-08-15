package com.quantlime.telegramfeed.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// videofeed.dto.SummaryPayload와 구조적으로 동일 - telegramfeed가 videofeed를
// 단방향으로만 참조하는 패키지 경계를 지키기 위해 별도로 둔다. 이제
// TelegramDigest.payload(JSON 문자열) 역직렬화 전용이다(2026-08-15 다이제스트
// 재설계 - 이전엔 TelegramSummary.payload용이었음). TelegramDigestPersistService
// .toPayloadJson()이 쓴 키(summary/key_points/macro_points/mentioned_tickers/
// caveat)와 맞춰야 한다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramSummaryPayload(
    String summary,
    @JsonProperty("key_points") List<String> keyPoints,
    @JsonProperty("macro_points") List<String> macroPoints,
    String caveat
) {
}
