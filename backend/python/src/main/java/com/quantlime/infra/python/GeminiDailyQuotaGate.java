package com.quantlime.infra.python;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Gemini 무료 티어 일일 쿼터 게이트(2026-09-14) - {@code PythonEngineClient.summarize()}
 * 하나에 걸려 있어 유튜브 요약과 텔레그램 다이제스트가 같은 예산을 공유한다.
 *
 * <p>키에 오늘 날짜(KST)를 박아 넣는 방식이라 자정마다 자연히 새 키로
 * 넘어간다 - 정확한 자정 만료 계산 대신 TTL을 넉넉히(2일) 둬서 옛 키가
 * 그냥 스스로 사라지게 한다.
 */
@Component
@RequiredArgsConstructor
public class GeminiDailyQuotaGate {

    private static final String KEY_PREFIX = "gemini:summarize:count:";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Duration KEY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redisTemplate;
    private final PythonEngineProperties properties;

    /**
     * 이번 호출을 오늘 카운터에 반영하고, 그 결과 설정된 일일 예산을
     * 넘었으면 true를 반환한다 - 호출부는 true면 quant-engine을 부르지
     * 않고 즉시 실패 처리해야 한다.
     */
    public boolean isExceeded() {
        String key = KEY_PREFIX + LocalDate.now(SEOUL);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, KEY_TTL);
        }
        return count != null && count > properties.getSummarizeDailyBudget();
    }
}
