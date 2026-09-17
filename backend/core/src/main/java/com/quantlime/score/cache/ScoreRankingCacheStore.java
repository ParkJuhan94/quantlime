package com.quantlime.score.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.score.dto.response.ScoreRankingResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code /api/dashboard/scores?watchlistOnly=false} 전체 랭킹 응답을 통째로
 * 캐싱한다(2026-09 성능 감사) - 배치가 하루 2회만 데이터를 바꾸는데 매
 * 요청마다 {@code eligibleStockCodes}(stock⨝stock_liquidity 조인) +
 * {@code findTopScoresOrderByCompositeScoreDesc}(전 종목 IN절 + 정렬) 두
 * 쿼리를 다시 도는 건 낭비다.
 *
 * <p>{@link com.quantlime.price.cache.PriceCacheStore}와 동일하게 {@link
 * StringRedisTemplate}을 재사용한다(신규 RedisConfig/RedisTemplate 빈
 * 불필요) - 값은 JSON 문자열로 직렬화해 저장한다. 컨트롤러의 {@code limit}
 * 상한(50, {@code ScoreController} 참고)만큼만 저장해두고 조회 시 그
 * 이하로 자른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreRankingCacheStore {

    private static final String KEY_PREFIX = "score:ranking:";
    // 배치는 하루 2회(16:00/20:10)뿐이라 정상 경로는 언제나 명시적 무효화
    // (MarketDataRefreshService가 정규화 직후 evict)다 - 이 TTL은 그 무효화가
    // 조용히 실패했을 때(예외 흡수 후 재시도 없음) stale 응답이 무한정
    // 남지 않게 하는 상한일 뿐이다.
    private static final Duration TTL = Duration.ofHours(1);
    private static final String METRIC_ACCESS = "score.ranking.cache.access";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public Optional<List<ScoreRankingResponse>> find(String scope) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(key(scope));
        } catch (DataAccessException e) {
            // Redis 장애를 캐시 미스로 취급한다 - 호출측(ScoreService)이 항상
            // DB 폴백을 갖고 있으므로 응답 자체는 계속 나간다(PriceCacheStore와
            // 동일한 판단, 2026-08-17 감사에서 확립된 패턴).
            log.warn("스코어 랭킹 캐시 조회 실패(Redis 연결): scope={}, error={}", scope, e.getMessage());
            meterRegistry.counter(METRIC_ACCESS, "result", "miss").increment();
            return Optional.empty();
        }
        if (json == null) {
            meterRegistry.counter(METRIC_ACCESS, "result", "miss").increment();
            return Optional.empty();
        }
        try {
            List<ScoreRankingResponse> ranking =
                objectMapper.readValue(json, new TypeReference<List<ScoreRankingResponse>>() {
                });
            meterRegistry.counter(METRIC_ACCESS, "result", "hit").increment();
            return Optional.of(ranking);
        } catch (JsonProcessingException e) {
            log.warn("스코어 랭킹 캐시 파싱 실패: scope={}, error={}", scope, e.getMessage(), e);
            meterRegistry.counter(METRIC_ACCESS, "result", "miss").increment();
            return Optional.empty();
        }
    }

    public void save(String scope, List<ScoreRankingResponse> ranking) {
        try {
            String json = objectMapper.writeValueAsString(ranking);
            redisTemplate.opsForValue().set(key(scope), json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("스코어 랭킹 캐시 저장 실패(직렬화): scope={}, error={}", scope, e.getMessage(), e);
        } catch (DataAccessException e) {
            log.warn("스코어 랭킹 캐시 저장 실패(Redis 연결): scope={}, error={}", scope, e.getMessage());
        }
    }

    /**
     * scope 3종(all/domestic/overseas) 키를 한꺼번에 지운다 - 배치
     * ({@code MarketDataRefreshService})가 국내·해외 정규화를 모두 마친
     * 뒤 호출한다. 개별 scope만 지우면 "domestic 배치만 끝난 상태"에서
     * all/overseas 키가 갱신 전 상태로 남는 애매한 창이 생겨, 세 키를
     * 항상 같이 무효화한다.
     */
    public void evictAll() {
        try {
            redisTemplate.delete(List.of(key("all"), key("domestic"), key("overseas")));
        } catch (DataAccessException e) {
            log.warn("스코어 랭킹 캐시 무효화 실패(Redis 연결): error={}", e.getMessage());
        }
    }

    private String key(String scope) {
        return KEY_PREFIX + scope;
    }
}
