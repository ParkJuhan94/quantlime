package com.quantlime.market.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.market.dto.response.MarketRankingResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// PriceCacheStoreTest와 동일 패턴(Mockito로 StringRedisTemplate/ValueOperations를
// 직접 목(mock) - 실제 Redis 컨테이너 없이 직렬화/키/TTL을 검증) - 이
// 클래스도 PriceCacheStore와 동일하게 "새 RedisTemplate 빈 없이
// StringRedisTemplate + JSON 문자열 저장"을 쓰기 때문.
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarketRankingCacheTest {

    private static final String SCOPE = "domestic";
    private static final String REDIS_KEY = "market:ranking:domestic";
    // MarketRankingCache.REDIS_TTL(private static final)과 동일한 값.
    private static final Duration REDIS_TTL = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MarketRankingCache marketRankingCache;

    @BeforeEach
    void setUp() {
        marketRankingCache = new MarketRankingCache(SCOPE, redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("[update는 랭킹 전체를 JSON으로 직렬화해 scope별 키에 TTL과 함께 쓴다]")
    void update_serializesAndWritesToRedisWithTtl() throws Exception {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        List<MarketRankingResponse> ranking = List.of(ranking("005930", 1.0));

        // when
        marketRankingCache.update(ranking);

        // then
        verify(valueOperations).set(REDIS_KEY, objectMapper.writeValueAsString(ranking), REDIS_TTL);
    }

    @Test
    @DisplayName("[상승률 내림차순으로 상위 N개를 반환한다]")
    void getGainers_returnsTopNByChangeRateDesc() throws Exception {
        // given
        stubRedisValue(List.of(ranking("005930", 1.0), ranking("000660", 5.0), ranking("035420", -3.0)));

        // when
        List<MarketRankingResponse> result = marketRankingCache.getGainers(2, null);

        // then
        assertThat(result).extracting(MarketRankingResponse::stockCode)
            .containsExactly("000660", "005930");
    }

    @Test
    @DisplayName("[하락률 오름차순(가장 많이 떨어진 순)으로 상위 N개를 반환한다]")
    void getLosers_returnsTopNByChangeRateAsc() throws Exception {
        // given
        stubRedisValue(List.of(ranking("005930", 1.0), ranking("000660", 5.0), ranking("035420", -3.0)));

        // when
        List<MarketRankingResponse> result = marketRankingCache.getLosers(2, null);

        // then
        assertThat(result).extracting(MarketRankingResponse::stockCode)
            .containsExactly("035420", "005930");
    }

    @Test
    @DisplayName("[limit이 전체 개수보다 크면 있는 만큼만 반환한다]")
    void getGainers_limitLargerThanSize_returnsAll() throws Exception {
        // given
        stubRedisValue(List.of(ranking("005930", 1.0)));

        // when
        List<MarketRankingResponse> result = marketRankingCache.getGainers(10, null);

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("[Redis에 키가 없으면(TTL 만료 등) 빈 목록을 반환한다]")
    void getGainers_noRedisValue_returnsEmpty() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(REDIS_KEY)).willReturn(null);

        // when
        List<MarketRankingResponse> result = marketRankingCache.getGainers(10, null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("[stockCodes로 필터링하면 그 코드들만 대상으로 정렬한다]")
    void getGainers_withStockCodesFilter_onlyRanksWithinThatSet() throws Exception {
        // given: 관심종목만 보기 토글 - 상위권(000660) 밖에 있는 035420도
        // 필터 대상이면 정확히 걸러져야 한다.
        stubRedisValue(List.of(ranking("005930", 1.0), ranking("000660", 5.0), ranking("035420", -3.0)));

        // when
        List<MarketRankingResponse> result =
            marketRankingCache.getGainers(10, Set.of("005930", "035420"));

        // then
        assertThat(result).extracting(MarketRankingResponse::stockCode)
            .containsExactly("005930", "035420");
    }

    @Test
    @DisplayName("[Redis 연결이 끊기면 직전 로컬 스냅샷을 그대로 서빙한다(stale-serve)]")
    void getGainers_redisConnectionFailure_servesStaleLocalSnapshot() throws Exception {
        // given: 첫 조회는 성공해 로컬 캐시가 채워지고, 로컬 TTL(1초)이
        // 지난 뒤의 두 번째 조회는 Redis 연결 실패를 겪는다.
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(REDIS_KEY))
            .willReturn(objectMapper.writeValueAsString(List.of(ranking("005930", 1.0))))
            .willThrow(new RedisConnectionFailureException("연결 끊김"));

        List<MarketRankingResponse> first = marketRankingCache.getGainers(10, null);
        Thread.sleep(1_100); // 로컬 TTL(1초)을 넘겨 다음 호출이 다시 Redis를 읽게 한다

        // when
        List<MarketRankingResponse> second = marketRankingCache.getGainers(10, null);

        // then: 두 번째 호출도 예외 없이 첫 스냅샷을 그대로 반환한다
        assertThat(first).extracting(MarketRankingResponse::stockCode).containsExactly("005930");
        assertThat(second).isEqualTo(first);
    }

    private void stubRedisValue(List<MarketRankingResponse> ranking) throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(REDIS_KEY)).willReturn(objectMapper.writeValueAsString(ranking));
    }

    private MarketRankingResponse ranking(String stockCode, double changeRate) {
        return new MarketRankingResponse(stockCode, stockCode + "-name", "전기전자", 10000.0, changeRate, null, null, null, null, true);
    }
}
