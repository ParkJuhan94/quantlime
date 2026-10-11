package com.quantlime.notification.cache;

import java.time.Duration;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 사분면 변화 알림의 중복 발송 방지 장부. Kafka는 at-least-once라 같은 사용자 요청이
 * 재전달되거나 재시도될 수 있고, 스코어가 갱신되지 않은 날(휴장 등)에도 "최신 행 vs
 * 직전 행" 비교는 같은 결과를 내므로, (사용자, 종목, 스코어 산출일)을 한 번만 통지하도록
 * Redis SET에 기록한다. TTL이 지나 장부가 비어도 판정 대상이 최근 며칠 내 스코어로 제한돼
 * (QuadrantChangeAlertService) 오래된 변화가 되살아나지 않는다.
 */
@Component
@RequiredArgsConstructor
public class QuadrantAlertSentStore {

    private static final String KEY_PREFIX = "notification:quadrant:sent:";
    private static final Duration TTL = Duration.ofDays(14);

    private final StringRedisTemplate redisTemplate;

    /** 처음 기록하는 항목이면 true, 이미 통지한 항목이면 false. */
    public boolean markIfNew(Long userId, String stockCode, LocalDate scoreDate) {
        String key = key(userId);
        Long added = redisTemplate.opsForSet().add(key, member(stockCode, scoreDate));
        redisTemplate.expire(key, TTL);
        return added != null && added > 0;
    }

    /** 발송 실패 시 기록을 되돌려 재시도에서 다시 통지되게 한다. */
    public void unmark(Long userId, String stockCode, LocalDate scoreDate) {
        redisTemplate.opsForSet().remove(key(userId), member(stockCode, scoreDate));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private String member(String stockCode, LocalDate scoreDate) {
        return stockCode + ":" + scoreDate;
    }
}
