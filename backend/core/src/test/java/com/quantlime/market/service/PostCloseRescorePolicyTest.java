package com.quantlime.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PostCloseRescorePolicyTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PostCloseRescorePolicy policyAt(String time) {
        Clock clock = Clock.fixed(TODAY.atTime(LocalTime.parse(time)).atZone(KST).toInstant(), KST);
        return new PostCloseRescorePolicy(redisTemplate, clock);
    }

    @Test
    @DisplayName("[마감 후(15:41)이고 오늘 가격·스코어가 모두 오늘이며 마커가 없으면 재계산한다]")
    void postClose_todayScoreAndPrice_noMarker_rescores() {
        given(redisTemplate.hasKey("market:score:post-close:2026-10-12")).willReturn(false);

        assertThat(policyAt("15:41:00").shouldRescore(TODAY, TODAY)).isTrue();
    }

    @Test
    @DisplayName("[장중(15:34 이전)에는 재계산하지 않는다 - 장중 캐치업이 마커를 막지 않게]")
    void beforePostClose_doesNotRescore() {
        assertThat(policyAt("12:00:00").shouldRescore(TODAY, TODAY)).isFalse();
        assertThat(policyAt("15:34:59").shouldRescore(TODAY, TODAY)).isFalse();
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("[스코어나 가격이 오늘이 아니면 기존 날짜 비교에 맡기고 재계산하지 않는다]")
    void notToday_doesNotRescore() {
        assertThat(policyAt("16:00:00").shouldRescore(TODAY.minusDays(1), TODAY)).isFalse();
        assertThat(policyAt("16:00:00").shouldRescore(TODAY, TODAY.minusDays(1))).isFalse();
        assertThat(policyAt("16:00:00").shouldRescore(null, TODAY)).isFalse();
    }

    @Test
    @DisplayName("[이미 마감 후 재계산을 끝낸 날(마커 있음)은 재기동 캐치업이 전종목을 다시 계산하지 않는다]")
    void markerPresent_doesNotRescore() {
        given(redisTemplate.hasKey("market:score:post-close:2026-10-12")).willReturn(true);

        assertThat(policyAt("18:00:00").shouldRescore(TODAY, TODAY)).isFalse();
    }

    @Test
    @DisplayName("[Redis 조회가 실패하면 가격 갱신을 막지 않고 재계산 없이 진행한다]")
    void redisFailure_doesNotRescore() {
        willThrow(new IllegalStateException("redis down")).given(redisTemplate).hasKey(anyString());

        assertThat(policyAt("16:00:00").shouldRescore(TODAY, TODAY)).isFalse();
    }

    @Test
    @DisplayName("[마커는 마감 후에만 기록하고 장중에는 남기지 않는다]")
    void marker_recordedOnlyPostClose() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        policyAt("16:30:00").markDoneIfPostClose();
        policyAt("11:00:00").markDoneIfPostClose();

        verify(valueOperations).set("market:score:post-close:2026-10-12", "1", Duration.ofDays(2));
    }
}
