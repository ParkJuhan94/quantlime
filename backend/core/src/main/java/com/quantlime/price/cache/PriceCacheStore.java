package com.quantlime.price.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.price.dto.response.PriceSnapshot;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
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

    /**
     * 스윕 1틱(국내 최대 2,700종목)마다 종목당 개별 SET 왕복을 하던 것을
     * 파이프라인 하나로 묶는다(2026-08-19) - Redis 파이프라이닝은 여러
     * 명령을 큐에 쌓아 한 번의 네트워크 왕복으로 실행하는 것뿐이라, 명령
     * 자체(TTL 포함 SET)는 개별 저장 때와 동일하게 수행된다. 직렬화
     * 실패는 해당 종목만 건너뛰고(다른 종목 저장에 영향 없음), 파이프라인
     * 실행 자체(Redis 연결 끊김 등)가 실패하면 {@link #save}와 동일하게
     * 흡수한다 - 다음 틱이 다시 시도하므로 유실이 누적되지 않는다.
     */
    @SuppressWarnings("unchecked")
    public void saveAll(List<PriceSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }
        try {
            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) {
                    for (PriceSnapshot snapshot : snapshots) {
                        try {
                            String json = objectMapper.writeValueAsString(snapshot);
                            operations.opsForValue().set(key(snapshot.stockCode()), json, TTL);
                        } catch (JsonProcessingException e) {
                            log.warn("시세 캐시 일괄 저장 중 직렬화 실패(해당 종목만 스킵): stockCode={}, error={}",
                                snapshot.stockCode(), e.getMessage(), e);
                        }
                    }
                    return null;
                }
            });
        } catch (DataAccessException e) {
            log.warn("시세 캐시 일괄 저장 실패(Redis 연결): count={}, error={}", snapshots.size(), e.getMessage());
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

    /**
     * 3초 주기 릴레이가 관심종목 수만큼 순차 GET을 반복하던 것을 파이프라인
     * 하나로 묶는다(2026-08-19) - {@link #find}와 동일하게 Redis 장애는
     * 빈 결과로 흡수하고(다음 틱 재시도), 개별 종목 파싱 실패는 그 종목만
     * 결과에서 빠진다(다른 종목에 영향 없음). 반환 맵에 없는 종목은 캐시
     * 미스로 취급한다(find가 Optional.empty()를 반환하는 것과 동일한 의미).
     */
    @SuppressWarnings("unchecked")
    public Map<String, PriceSnapshot> findAll(List<String> stockCodes) {
        if (stockCodes.isEmpty()) {
            return Map.of();
        }

        List<Object> rawResults;
        try {
            rawResults = redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) {
                    for (String stockCode : stockCodes) {
                        operations.opsForValue().get(key(stockCode));
                    }
                    return null;
                }
            });
        } catch (DataAccessException e) {
            log.warn("시세 캐시 일괄 조회 실패(Redis 연결): count={}, error={}", stockCodes.size(), e.getMessage());
            return Map.of();
        }

        Map<String, PriceSnapshot> result = new LinkedHashMap<>();
        for (int i = 0; i < stockCodes.size(); i++) {
            Object raw = rawResults.get(i);
            if (raw == null) {
                continue;
            }
            String stockCode = stockCodes.get(i);
            try {
                result.put(stockCode, objectMapper.readValue((String) raw, PriceSnapshot.class));
            } catch (JsonProcessingException e) {
                log.warn("시세 캐시 일괄 조회 중 파싱 실패(해당 종목만 스킵): stockCode={}, error={}",
                    stockCode, e.getMessage(), e);
            }
        }
        return result;
    }

    private String key(String stockCode) {
        return KEY_PREFIX + stockCode;
    }
}
