package com.quantlime.market.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 정규장 마감 후 배치가 "오늘 스코어가 이미 있어도" 한 번은 다시 계산하게 하는 판단.
 *
 * <p>스코어는 {@code 최신 스코어 산출일 < 최신 가격일}일 때만 재계산된다
 * ({@code MarketDataRefreshService#needsScoreRefresh}). 낮에 배포·재기동으로 기동 캐치업이
 * 한 번 돌면 장중 미완성 일봉으로 오늘 스코어가 만들어지고, 마감 후 배치는 가격만 갱신한 채
 * 그 스코어를 그대로 둔다 - 마감 후 배치가 하루 한 번뿐인 지금(2026-10-09 단일화)은 사분면
 * 알림까지 장중 데이터 기준이 된다. 그래서 마감 후(15:35~) 첫 배치에서만 오늘 스코어를 다시
 * 계산하고, 그 배치가 끝나면 날짜별 마커를 남겨 같은 날 재기동 캐치업이 전종목을 또
 * 재계산하지 않게 한다.
 *
 * <p>Redis 장애는 "재계산 안 함"(기존 동작)으로 흡수한다 - 이 판단이 가격 갱신 자체를
 * 실패시키면 안 된다.
 */
@Slf4j
@Component
public class PostCloseRescorePolicy {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final LocalTime POST_CLOSE_FROM = LocalTime.of(15, 35);
    private static final String KEY_PREFIX = "market:score:post-close:";
    private static final Duration MARKER_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Autowired
    public PostCloseRescorePolicy(StringRedisTemplate redisTemplate) {
        this(redisTemplate, Clock.system(KST));
    }

    PostCloseRescorePolicy(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    /** 오늘 가격·스코어가 모두 오늘 날짜이고 마감 후이며 아직 마감 후 재계산을 끝내지 않았으면 true. */
    public boolean shouldRescore(@Nullable LocalDate latestScoreDate, LocalDate latestPriceDate) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        if (!today.equals(latestScoreDate) || !today.equals(latestPriceDate) || !isPostClose(now)) {
            return false;
        }
        try {
            return !Boolean.TRUE.equals(redisTemplate.hasKey(key(today)));
        } catch (RuntimeException e) {
            log.warn("마감 후 재계산 마커 조회 실패, 재계산 없이 진행: {}", e.getMessage());
            return false;
        }
    }

    /** 마감 후에 돈 배치가 끝났을 때만 마커를 남긴다 - 장중 캐치업이 마커를 남기면 마감 후 재계산이 막힌다. */
    public void markDoneIfPostClose() {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!isPostClose(now)) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(now.toLocalDate()), "1", MARKER_TTL);
        } catch (RuntimeException e) {
            log.warn("마감 후 재계산 마커 기록 실패: {}", e.getMessage());
        }
    }

    private boolean isPostClose(LocalDateTime now) {
        return !now.toLocalTime().isBefore(POST_CLOSE_FROM);
    }

    private String key(LocalDate date) {
        return KEY_PREFIX + date;
    }
}
