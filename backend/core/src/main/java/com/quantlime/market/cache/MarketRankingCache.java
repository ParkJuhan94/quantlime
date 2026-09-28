package com.quantlime.market.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.market.dto.response.MarketRankingResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * "관심종목만 보기(gainers/losers)" 랭킹의 최신 스냅샷. 리더 인스턴스의
 * 스케줄러(국내: {@code DomesticMarketPriceSweepScheduler}, 해외:
 * {@code OverseasWatchlistPriceScheduler})가 매 틱 {@link #update}로
 * Redis에 쓰고, 모든 인스턴스가 짧은 로컬 TTL({@value #LOCAL_TTL_SECONDS}초)
 * 캐시를 거쳐 읽는다.
 *
 * <p>이전 구현({@code DomesticMarketRankingCache})은 JVM 로컬 메모리 하나뿐이었다
 * - 단일 인스턴스 전제에선 문제없었지만, {@code PriceRelayLeaderGate}로
 * 리더만 스윕을 돌게 되면서(2026-09-25) follower 인스턴스에서는 이 값이
 * 영원히 비는 문제가 생겨 Redis로 옮겼다. 해외는 기존에 랭킹 전용
 * 캐시가 따로 없이 {@code MarketRankingService}가 매 요청마다
 * {@code PriceCacheStore}+{@code PreviousCloseCache}를 직접 읽어
 * 계산했는데, 이 클래스로 같은 방식(국내와 동일한 패턴)으로 통일했다
 * (2026-09-25 - 인스턴스 수만큼의 계산 대신, 리더만 계산해 Redis에
 * 한 번 쓰고 나머지는 읽기만 함).
 *
 * <p>국내/해외 두 인스턴스는 {@code MarketRankingCacheConfig}가 Bean
 * 2개로 등록한다({@code WatchlistedStockCodeCache}와 동일한 "생성자로
 * 시장 범위 주입" 패턴).
 *
 * <p>Redis 연결 자체가 실패하면(끊김 등) 직전 로컬 스냅샷을 그대로
 * 서빙한다(stale-serve, docs/00-sre/SRE.md "캐시" 절 원칙 - 랭킹은
 * 사용자에게 보여주는 값이라 빈 목록보다 "약간 오래된 값"이 낫다).
 * 반면 Redis 키 자체가 없으면(TTL 만료 - 리더가 5초 이상 갱신을 안
 * 했다는 뜻, 로컬 갱신 주기 1초의 5배 여유를 넘긴 것이라 이미 "일시적
 * 지연"을 넘어선 상태로 판단) 빈 목록으로 비운다 - 예전 구현의 "재시작
 * 시 초기화" 동작과 동일한 의미를 유지한다.
 */
@Slf4j
public class MarketRankingCache {

    private static final Duration REDIS_TTL = Duration.ofSeconds(5);
    private static final int LOCAL_TTL_SECONDS = 1;
    private static final String KEY_PREFIX = "market:ranking:";
    private static final TypeReference<List<MarketRankingResponse>> RANKING_LIST_TYPE =
        new TypeReference<>() { };

    private final String scope;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private volatile List<MarketRankingResponse> localCache = List.of();
    private volatile Instant localRefreshedAt = Instant.EPOCH;

    public MarketRankingCache(String scope, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.scope = scope;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 리더 인스턴스만 호출 - 이번 틱 랭킹 전체를 Redis에 쓴다. */
    public void update(List<MarketRankingResponse> ranking) {
        try {
            String json = objectMapper.writeValueAsString(ranking);
            redisTemplate.opsForValue().set(key(), json, REDIS_TTL);
        } catch (JsonProcessingException e) {
            log.warn("랭킹 캐시 저장 실패(직렬화): scope={}, error={}", scope, e.getMessage(), e);
        } catch (DataAccessException e) {
            log.warn("랭킹 캐시 저장 실패(Redis 연결): scope={}, error={}", scope, e.getMessage());
        }
    }

    public List<MarketRankingResponse> getGainers(int limit, Set<String> stockCodes) {
        return filtered(stockCodes)
            .sorted(Comparator.comparingDouble(MarketRankingResponse::changeRate).reversed())
            .limit(limit)
            .toList();
    }

    public List<MarketRankingResponse> getLosers(int limit, Set<String> stockCodes) {
        return filtered(stockCodes)
            .sorted(Comparator.comparingDouble(MarketRankingResponse::changeRate))
            .limit(limit)
            .toList();
    }

    /**
     * stockCodes가 null이면 전종목, 아니면 그 코드들만 - "관심종목만 보기"
     * 토글용. 전종목 스냅샷 안에서 필터링하므로(top-50 등으로 미리 잘려
     * 있지 않음) 관심종목이 상위권 밖에 있어도 정확히 걸러진다.
     */
    private Stream<MarketRankingResponse> filtered(Set<String> stockCodes) {
        Stream<MarketRankingResponse> stream = readThrough().stream();
        return stockCodes == null ? stream : stream.filter(item -> stockCodes.contains(item.stockCode()));
    }

    private List<MarketRankingResponse> readThrough() {
        if (isStale()) {
            refresh();
        }
        return localCache;
    }

    private boolean isStale() {
        return Duration.between(localRefreshedAt, Instant.now()).getSeconds() >= LOCAL_TTL_SECONDS;
    }

    private synchronized void refresh() {
        if (!isStale()) {
            return; // 락 대기 중 다른 스레드가 이미 갱신함
        }
        // 실패해도 이 시각은 갱신한다 - 그러지 않으면 Redis 장애 중 모든
        // 요청이 매번 재시도해 장애를 오히려 연장시킨다(WatchlistedStockCodeCache와
        // 동일 원칙).
        localRefreshedAt = Instant.now();

        String json;
        try {
            json = redisTemplate.opsForValue().get(key());
        } catch (DataAccessException e) {
            log.warn("랭킹 캐시 조회 실패(Redis 연결), 직전 스냅샷 유지: scope={}, error={}", scope, e.getMessage());
            return; // localCache 그대로 유지(stale-serve)
        }
        if (json == null) {
            // 키가 아예 없음(TTL 만료 등) - 로컬 갱신 주기(1초)의 5배인
            // Redis TTL(5초)까지 넘겼다는 뜻이라 "일시적 지연"을 넘어선
            // 상태로 보고 비운다(예전 JVM-로컬 구현의 "재시작 시 초기화"와
            // 동일한 의미).
            localCache = List.of();
            return;
        }
        try {
            localCache = objectMapper.readValue(json, RANKING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("랭킹 캐시 파싱 실패, 직전 스냅샷 유지: scope={}, error={}", scope, e.getMessage(), e);
        }
    }

    private String key() {
        return KEY_PREFIX + scope;
    }
}
