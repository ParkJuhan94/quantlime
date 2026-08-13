package com.quantlime.videofeed.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 채널별 텔레그램 필터 설정(Phase 8 P7). ChannelFilterConfig(유튜브)와
 * 달리 영상 길이/업로드 속도 개념이 없다 - 대신 본문 글자 수 하한으로
 * "요약할 가치가 있는 글"만 통과시킨다(짧은 속보를 LLM에 태우면 Gemini
 * 무료 티어 일 쿼터를 빠르게 소진하므로, 필터 레벨에서 수요를 자르는 게
 * 1차 방어선 - docs/ROADMAP.md "Phase 8 P7" 참고).
 *
 * <p>Channel 엔티티가 이 타입을 직접 필드로 갖기 때문에(텔레그램 채널도
 * Channel을 그대로 재사용) videofeed.domain에 둔다 - telegramfeed 패키지가
 * videofeed.domain을 단방향으로만 참조하는 설계를 지키기 위함(반대 방향
 * 의존이 생기지 않도록).
 */
public record TelegramFilterConfig(
    @JsonProperty("min_char_count") int minCharCount,
    @JsonProperty("max_per_run") int maxPerRun,
    @JsonProperty("content_exclude") List<String> contentExclude,
    @JsonProperty("content_include") List<String> contentInclude
) {

    @JsonCreator
    public TelegramFilterConfig {
        contentExclude = contentExclude == null ? List.of() : contentExclude;
        contentInclude = contentInclude == null ? List.of() : contentInclude;
    }
}
