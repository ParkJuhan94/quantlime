package com.quantlime.price.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.price.dto.response.PriceSnapshot;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 종목별 최신 시세 스냅샷({@link PriceSnapshot})을 Redis에 저장한다.
 * {@code MarketPriceSweepScheduler}가 매 틱 적재하고, StockPriceService.getCurrentPrice가 이를 먼저 조회해
 * 미스일 때만 Toss를 직접 호출하는 read-through 캐시로도 재사용한다.
 *
 * <p>기존 TossTokenManager/RefreshTokenStore와 동일하게 {@link StringRedisTemplate}
 * 을 재사용한다(새 RedisConfig/RedisTemplate 빈 불필요) - 값은 JSON
 * 문자열로 직렬화해 저장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceCacheStore {

    private static final String KEY_PREFIX = "price:current:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void save(PriceSnapshot snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(key(snapshot.stockCode()), json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("시세 캐시 저장 실패: stockCode={}, error={}",
                snapshot.stockCode(), e.getMessage(), e);
        }
    }

    public Optional<PriceSnapshot> find(String stockCode) {
        String json = redisTemplate.opsForValue().get(key(stockCode));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, PriceSnapshot.class));
        } catch (JsonProcessingException e) {
            log.warn("시세 캐시 파싱 실패: stockCode={}, error={}", stockCode, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private String key(String stockCode) {
        return KEY_PREFIX + stockCode;
    }
}
