package com.quantlime.videofeed.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 채널별 필터 설정. velocityMultiplier가 0이면 업로드 속도 판정 없이
 * min_duration_sec/title_exclude/title_include만으로 필터링한다(개인
 * 채널처럼 median_velocity 산정이 의미 없는 경우).
 */
public record ChannelFilterConfig(
    @JsonProperty("min_duration_sec") int minDurationSec,
    @JsonProperty("velocity_multiplier") double velocityMultiplier,
    @JsonProperty("max_per_run") int maxPerRun,
    @JsonProperty("title_exclude") List<String> titleExclude,
    @JsonProperty("title_include") List<String> titleInclude
) {

    @JsonCreator
    public ChannelFilterConfig {
        titleExclude = titleExclude == null ? List.of() : titleExclude;
        titleInclude = titleInclude == null ? List.of() : titleInclude;
    }

    // channel.filter_config는 NOT NULL 컬럼이라 텔레그램 채널(Phase 8 P7)
    // 행에도 값이 있어야 한다(ddl-auto=update는 기존 NOT NULL을 nullable로
    // 못 되돌림). 텔레그램 파이프라인은 이 값을 절대 읽지 않으므로(플랫폼
    // 한정 조회로 분리됨 - ChannelRepository 참고) 전부 무해한 값으로 채운다.
    public static ChannelFilterConfig unusedForNonYoutube() {
        return new ChannelFilterConfig(0, 0.0, 0, List.of(), List.of());
    }
}
