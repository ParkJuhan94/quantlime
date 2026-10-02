package com.quantlime.score.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.market.domain.RankingPeriod;
import com.quantlime.score.dto.response.ScoreRankingResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ScoreRankingCacheStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private ScoreRankingCacheStore store;

    @BeforeEach
    void setUp() {
        store = new ScoreRankingCacheStore(redisTemplate, new ObjectMapper().findAndRegisterModules(), meterRegistry);
    }

    private double count(String result) {
        return meterRegistry.counter("score.ranking.cache.access", "result", result).count();
    }

    private ScoreRankingResponse score(String code) {
        return new ScoreRankingResponse(code, "종목" + code, "섹터", null, 1.0, 2.0, 70.0, 10.0, 20.0, 90.0,
            "BUY", false, null, false, 1234.5, null);
    }

    @Test
    @DisplayName("[저장한 랭킹은 JSON으로 직렬화돼 scope별 키에 1시간 TTL로 저장된다]")
    void save_writesJsonWithTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        store.save("all", List.of(score("000001")));

        verify(valueOperations).set(eq("score:ranking:all"), org.mockito.ArgumentMatchers.contains("000001"),
            eq(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("[캐시에 있는 JSON은 원래 응답 객체로 복원되고 hit로 집계된다]")
    void find_hit_deserializesAndCountsHit() throws Exception {
        // given
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(List.of(score("000001")));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("score:ranking:domestic")).willReturn(json);

        // when
        Optional<List<ScoreRankingResponse>> result = store.find("domestic");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().get(0).stockCode()).isEqualTo("000001");
        assertThat(result.get().get(0).avgTradingValue()).isEqualTo(1234.5);
        assertThat(count("hit")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[키가 없으면 빈 Optional이고 miss로 집계된다]")
    void find_absent_countsMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("score:ranking:all")).willReturn(null);

        assertThat(store.find("all")).isEmpty();
        assertThat(count("miss")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[Redis 장애는 캐시 미스로 취급해 예외 없이 DB 폴백이 가능하게 한다]")
    void find_redisDown_isTreatedAsMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willThrow(new QueryTimeoutException("timeout"));

        assertThat(store.find("all")).isEmpty();
        assertThat(count("miss")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[깨진 JSON도 예외 없이 미스로 취급한다]")
    void find_corruptJson_isTreatedAsMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("score:ranking:all")).willReturn("{not-json");

        assertThat(store.find("all")).isEmpty();
        assertThat(count("miss")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[저장 중 Redis 장애는 삼킨다(응답은 계속 나간다)]")
    void save_redisDown_isSwallowed() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        willThrow(new QueryTimeoutException("timeout")).given(valueOperations)
            .set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));

        assertThatCode(() -> store.save("all", List.of(score("000001")))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[evictAll은 all/domestic/overseas 각각의 기본 키와 기간별 키를 한 번에 지운다 - 애매한 부분 갱신 창을 만들지 않는다]")
    @SuppressWarnings("unchecked")
    void evictAll_deletesBaseAndPeriodKeysOfAllScopes() {
        store.evictAll();

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(redisTemplate).delete(captor.capture());
        List<String> expected = new ArrayList<>();
        for (String scope : List.of("all", "domestic", "overseas")) {
            expected.add("score:ranking:" + scope);
            for (RankingPeriod period : RankingPeriod.values()) {
                expected.add("score:ranking:" + ScoreRankingCacheStore.changeScope(scope, period));
            }
        }
        assertThat(captor.getValue()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(captor.getValue()).contains("score:ranking:all", "score:ranking:domestic", "score:ranking:overseas");
    }

    @Test
    @DisplayName("[무효화 중 Redis 장애도 삼킨다]")
    void evictAll_redisDown_isSwallowed() {
        willThrow(new QueryTimeoutException("timeout")).given(redisTemplate).delete(anyCollection());

        assertThatCode(() -> store.evictAll()).doesNotThrowAnyException();
    }
}
