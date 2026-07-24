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
}
