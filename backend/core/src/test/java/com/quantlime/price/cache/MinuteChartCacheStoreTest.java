package com.quantlime.price.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.price.dto.response.MinuteChartResponse;
import java.time.Duration;
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
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MinuteChartCacheStoreTest {

    private static final MinuteChartResponse RESPONSE = new MinuteChartResponse(
        List.of(new MinuteChartResponse.Candle(1_700_000_000L, 1, 2, 0.5, 1.5, 100)), "cursor-1");

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MinuteChartCacheStore store;

    @BeforeEach
    void setUp() {
        store = new MinuteChartCacheStore(redisTemplate, new ObjectMapper());
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("[커서 없는 최신 페이지는 짧은 TTL(15초)과 latest 키로 저장한다]")
    void save_latestPage_usesShortTtlAndLatestKey() {
        store.save("005930", null, RESPONSE);

        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq("chart:1m:005930:latest"), anyString(),
            org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(15)));
    }

    @Test
    @DisplayName("[before로 과거를 지정한 페이지는 긴 TTL(30분)로 저장한다]")
    void save_historyPage_usesLongTtl() {
        store.save("005930", "2026-09-01", RESPONSE);

        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq("chart:1m:005930:2026-09-01"), anyString(),
            org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("[저장한 JSON을 그대로 읽어 되돌린다]")
    void saveAndFind_roundTrip() {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        store.save("005930", null, RESPONSE);
        verify(valueOperations).set(anyString(), json.capture(), any(Duration.class));
        given(valueOperations.get("chart:1m:005930:latest")).willReturn(json.getValue());

        assertThat(store.find("005930", null)).contains(RESPONSE);
    }

    @Test
    @DisplayName("[캐시에 없으면 빈 값을 돌려준다]")
    void find_miss_returnsEmpty() {
        given(valueOperations.get(anyString())).willReturn(null);

        assertThat(store.find("005930", null)).isEmpty();
    }

    @Test
    @DisplayName("[Redis 장애와 깨진 JSON은 캐시 미스로 취급한다]")
    void find_failures_areMisses() {
        given(valueOperations.get("chart:1m:005930:latest"))
            .willThrow(new RedisConnectionFailureException("down"));
        given(valueOperations.get("chart:1m:000660:latest")).willReturn("{not-json");

        assertThat(store.find("005930", null)).isEqualTo(Optional.empty());
        assertThat(store.find("000660", null)).isEmpty();
    }

    @Test
    @DisplayName("[저장 중 Redis 장애나 직렬화 실패는 예외를 전파하지 않는다]")
    void save_failures_areSwallowed() throws Exception {
        willThrow(new RedisConnectionFailureException("down"))
            .given(valueOperations).set(anyString(), anyString(), any(Duration.class));
        assertThatCode(() -> store.save("005930", null, RESPONSE)).doesNotThrowAnyException();

        ObjectMapper failing = mock(ObjectMapper.class);
        given(failing.writeValueAsString(any())).willThrow(new JsonProcessingException("boom") {
        });
        MinuteChartCacheStore failingStore = new MinuteChartCacheStore(redisTemplate, failing);
        assertThatCode(() -> failingStore.save("005930", null, RESPONSE)).doesNotThrowAnyException();
    }
}
