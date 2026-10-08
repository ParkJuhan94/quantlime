package com.quantlime.price.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.price.dto.response.PriceSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Redis 장애·직렬화/파싱 실패를 "캐시 미스/건너뜀"으로 흡수하는 경로와 히트율 카운터 검증.
 * 정상 경로는 {@link PriceCacheStoreTest}가 맡는다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PriceCacheStoreFailureTest {

    private static final DataAccessException REDIS_DOWN = new RedisConnectionFailureException("down");

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private PriceCacheStore store;

    @BeforeEach
    void setUp() {
        store = new PriceCacheStore(redisTemplate, new ObjectMapper(), meterRegistry);
    }

    private double counter(String result) {
        return meterRegistry.counter("price.cache.access", "result", result).count();
    }

    private static JsonProcessingException jsonError() {
        return new JsonProcessingException("boom") {
        };
    }

    @Test
    @DisplayName("[save - Redis 연결이 끊겨도 예외를 전파하지 않는다]")
    void save_redisDown_isSwallowed() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        willThrow(REDIS_DOWN).given(valueOperations).set(anyString(), anyString(), any(Duration.class));

        assertThatCode(() -> store.save(new PriceSnapshot("005930", 1.0, 0.0, "ts")))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[save - 직렬화에 실패하면 Redis에 쓰지 않고 넘어간다]")
    void save_serializationFails_skipsWrite() throws Exception {
        ObjectMapper failing = mock(ObjectMapper.class);
        given(failing.writeValueAsString(any())).willThrow(jsonError());
        PriceCacheStore failingStore = new PriceCacheStore(redisTemplate, failing, meterRegistry);

        assertThatCode(() -> failingStore.save(new PriceSnapshot("005930", 1.0, 0.0, "ts")))
            .doesNotThrowAnyException();
        verify(redisTemplate, never()).opsForValue();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("[saveAll - 빈 목록은 파이프라인을 실행하지 않는다]")
    void saveAll_empty_skipsPipeline() {
        store.saveAll(List.of());

        verify(redisTemplate, never()).executePipelined(any(SessionCallback.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("[saveAll - 파이프라인 실행이 Redis 장애로 실패해도 예외를 전파하지 않는다]")
    void saveAll_redisDown_isSwallowed() {
        given(redisTemplate.executePipelined(any(SessionCallback.class))).willThrow(REDIS_DOWN);

        assertThatCode(() -> store.saveAll(List.of(new PriceSnapshot("005930", 1.0, 0.0, "ts"))))
            .doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("[saveAll - 한 종목 직렬화가 실패해도 나머지 종목은 저장한다]")
    void saveAll_oneSerializationFails_savesTheRest() throws Exception {
        ObjectMapper partial = mock(ObjectMapper.class);
        PriceSnapshot bad = new PriceSnapshot("BAD", 1.0, 0.0, "ts");
        PriceSnapshot good = new PriceSnapshot("005930", 2.0, 0.0, "ts");
        given(partial.writeValueAsString(bad)).willThrow(jsonError());
        given(partial.writeValueAsString(good)).willReturn("{good}");
        PriceCacheStore partialStore = new PriceCacheStore(redisTemplate, partial, meterRegistry);

        RedisOperations<String, String> operations = mock(RedisOperations.class);
        ValueOperations<String, String> pipelined = mock(ValueOperations.class);
        given(operations.opsForValue()).willReturn(pipelined);
        ArgumentCaptor<SessionCallback<Object>> captor = ArgumentCaptor.forClass(SessionCallback.class);
        given(redisTemplate.executePipelined(captor.capture())).willReturn(List.of());

        partialStore.saveAll(List.of(bad, good));
        captor.getValue().execute(operations);

        verify(pipelined).set("price:current:005930", "{good}", Duration.ofMinutes(5));
        verify(pipelined, never()).set(org.mockito.ArgumentMatchers.eq("price:current:BAD"), anyString(),
            any(Duration.class));
    }

    @Test
    @DisplayName("[find - Redis 장애는 캐시 미스로 취급하고 miss를 기록한다]")
    void find_redisDown_isMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("price:current:005930")).willThrow(REDIS_DOWN);

        assertThat(store.find("005930")).isEmpty();
        assertThat(counter("miss")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[find - 저장된 JSON이 깨져 있으면 캐시 미스로 취급한다]")
    void find_corruptJson_isMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("price:current:005930")).willReturn("{not-json");

        assertThat(store.find("005930")).isEmpty();
        assertThat(counter("miss")).isEqualTo(1.0);
        assertThat(counter("hit")).isZero();
    }

    @Test
    @DisplayName("[find - 정상 조회는 hit를 기록한다]")
    void find_hit_recordsHit() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("price:current:005930"))
            .willReturn(new ObjectMapper().writeValueAsString(new PriceSnapshot("005930", 1.0, 0.0, "ts")));

        assertThat(store.find("005930")).isPresent();
        assertThat(counter("hit")).isEqualTo(1.0);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("[findAll - Redis 장애는 빈 맵으로 흡수한다]")
    void findAll_redisDown_returnsEmptyMap() {
        given(redisTemplate.executePipelined(any(SessionCallback.class))).willThrow(REDIS_DOWN);

        assertThat(store.findAll(List.of("005930"))).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("[findAll - 깨진 JSON 종목만 빠지고 hit/miss 카운터는 결과 기준으로 집계한다]")
    void findAll_corruptEntry_isSkippedAndCounted() throws Exception {
        String good = new ObjectMapper().writeValueAsString(new PriceSnapshot("005930", 1.0, 0.0, "ts"));
        given(redisTemplate.executePipelined(any(SessionCallback.class)))
            .willReturn(Arrays.asList(good, "{not-json", null));

        Map<String, PriceSnapshot> result = store.findAll(List.of("005930", "000660", "035420"));

        assertThat(result).containsOnlyKeys("005930");
        assertThat(counter("hit")).isEqualTo(1.0);
        assertThat(counter("miss")).isEqualTo(2.0);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("[findAll - 파이프라인 콜백은 종목마다 GET 명령을 큐잉한다]")
    void findAll_callback_queuesGetPerStockCode() {
        RedisOperations<String, String> operations = mock(RedisOperations.class);
        ValueOperations<String, String> pipelined = mock(ValueOperations.class);
        given(operations.opsForValue()).willReturn(pipelined);
        ArgumentCaptor<SessionCallback<Object>> captor = ArgumentCaptor.forClass(SessionCallback.class);
        given(redisTemplate.executePipelined(captor.capture())).willReturn(Arrays.asList(null, null));

        store.findAll(List.of("005930", "000660"));
        captor.getValue().execute(operations);

        verify(pipelined).get("price:current:005930");
        verify(pipelined).get("price:current:000660");
    }
}
