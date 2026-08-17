package com.quantlime.price.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.price.dto.response.PriceSnapshot;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 종목별 최신 시세 스냅샷({@link PriceSnapshot})을 Redis에 저장한다.
 * {@code DomesticMarketPriceSweepScheduler}가 매 틱 적재하고, StockPriceService.getCurrentPrice가 이를 먼저 조회해
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
            log.warn("시세 캐시 저장 실패(직렬화): stockCode={}, error={}",
                snapshot.stockCode(), e.getMessage(), e);
        } catch (DataAccessException e) {
            // Redis 연결 자체가 끊긴 경우(RedisConnectionFailureException 등) -
            // 이전엔 여기서 예외가 전파돼 DomesticMarketPriceSweepScheduler 등
            // 호출측의 SafeExecutor까지 올라가서야 잡혔다. 저장 실패는 다음
            // 틱(국내 100ms/해외 3초)이 다시 시도하므로 여기서 흡수해도
            // 손실이 누적되지 않는다(2026-08-17).
            log.warn("시세 캐시 저장 실패(Redis 연결): stockCode={}, error={}",
                snapshot.stockCode(), e.getMessage());
        }
    }

    public Optional<PriceSnapshot> find(String stockCode) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(key(stockCode));
        } catch (DataAccessException e) {
            // Redis 장애를 "캐시 미스"로 취급한다 - 호출측(StockPriceService)이
            // 이미 미스 시 Toss를 직접 호출하는 read-through 폴백을 갖고 있어
            // (클래스 주석 참고), 여기서 예외를 삼켜도 응답은 계속 나간다(단,
            // Toss 직접 호출 경로 자체는 이 클래스 책임 밖이라 그만큼 느려질
            // 수는 있음, 2026-08-17).
            log.warn("시세 캐시 조회 실패(Redis 연결): stockCode={}, error={}", stockCode, e.getMessage());
            return Optional.empty();
        }
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
