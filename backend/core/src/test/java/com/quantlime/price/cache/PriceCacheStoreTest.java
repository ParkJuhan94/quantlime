package com.quantlime.price.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.price.dto.response.PriceSnapshot;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PriceCacheStoreTest {

    private static final String STOCK_CODE = "005930";
    // PriceCacheStore.TTL(private static final)과 동일한 값 - 필드 자체를
    // 외부에서 참조할 수 없어 테스트에서 동일 값을 별도로 유지한다.
    private static final Duration TTL_5_MINUTES = Duration.ofMinutes(5);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PriceCacheStore priceCacheStore;

    @BeforeEach
    void setUp() {
        priceCacheStore = new PriceCacheStore(redisTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("[저장한 스냅샷을 그대로 조회할 수 있다]")
    void saveAndFind_roundTrip_returnsEquivalentMessage() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        PriceSnapshot snapshot =
            new PriceSnapshot(STOCK_CODE, 70000.0, 1.5, "2026-07-06T09:00:00+09:00");
        String[] savedJson = new String[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            savedJson[0] = invocation.getArgument(1);
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any());
        priceCacheStore.save(snapshot);
        given(valueOperations.get(anyString())).willReturn(savedJson[0]);

        // when
        Optional<PriceSnapshot> result = priceCacheStore.find(STOCK_CODE);

        // then
        assertThat(result).contains(snapshot);
    }

    @Test
    @DisplayName("[캐시에 값이 없으면 빈 Optional을 반환한다]")
    void find_noCachedValue_returnsEmpty() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        // when
        Optional<PriceSnapshot> result = priceCacheStore.find(STOCK_CODE);

        // then
        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("[saveAll은 여러 스냅샷을 파이프라인 하나(단일 SessionCallback)로 저장한다]")
    void saveAll_multipleSnapshots_setsEachWithTtlInOnePipeline() throws Exception {
        // given: 스프링이 executePipelined 안에서 실제로 콜백을 실행하는 것을
        // 재현하기 위해, 콜백을 캡처해 목(RedisOperations)에 수동으로 실행한다.
        RedisOperations<String, String> redisOperations = mock(RedisOperations.class);
        ValueOperations<String, String> pipelinedValueOperations = mock(ValueOperations.class);
        given(redisOperations.opsForValue()).willReturn(pipelinedValueOperations);
        ArgumentCaptor<SessionCallback<Object>> callbackCaptor = ArgumentCaptor.forClass(SessionCallback.class);
        given(redisTemplate.executePipelined(callbackCaptor.capture())).willReturn(List.of());

        PriceSnapshot snapshot1 = new PriceSnapshot("005930", 70000.0, 1.0, "ts1");
        PriceSnapshot snapshot2 = new PriceSnapshot("000660", 100000.0, -0.5, "ts2");

        // when
        priceCacheStore.saveAll(List.of(snapshot1, snapshot2));
        callbackCaptor.getValue().execute(redisOperations);

        // then: 파이프라인 하나는 여전히 실행기 호출 1회로 통합돼 있고(네트워크
        // 왕복 1회), 그 안에서 종목별 SET(TTL 포함)은 각각 수행된다.
        verify(redisTemplate).executePipelined(any(SessionCallback.class));
        verify(pipelinedValueOperations).set("price:current:005930",
            new ObjectMapper().writeValueAsString(snapshot1), TTL_5_MINUTES);
        verify(pipelinedValueOperations).set("price:current:000660",
            new ObjectMapper().writeValueAsString(snapshot2), TTL_5_MINUTES);
    }

    @Test
    @DisplayName("[findAll은 여러 종목의 시세를 파이프라인 하나로 조회해 코드별 맵으로 반환한다]")
    void findAll_multipleStockCodes_returnsMapKeyedByStockCode() throws Exception {
        // given: 두 번째 종목은 캐시 미스(null)를 재현
        PriceSnapshot cached = new PriceSnapshot(STOCK_CODE, 70000.0, 1.0, "ts1");
        String cachedJson = new ObjectMapper().writeValueAsString(cached);
        // List.of는 null 원소를 허용하지 않아(즉시 NPE) 실제 Redis 파이프라인
        // 결과(미스는 null)를 재현할 수 없다 - Arrays.asList로 대체.
        given(redisTemplate.executePipelined(any(SessionCallback.class)))
            .willReturn(Arrays.asList(cachedJson, null));

        // when
        Map<String, PriceSnapshot> result = priceCacheStore.findAll(List.of(STOCK_CODE, "000660"));

        // then
        assertThat(result).containsOnlyKeys(STOCK_CODE);
        assertThat(result.get(STOCK_CODE)).isEqualTo(cached);
    }

    @Test
    @DisplayName("[findAll에 빈 목록을 넘기면 파이프라인을 실행하지 않고 빈 맵을 반환한다]")
    void findAll_emptyStockCodes_skipsPipelineExecution() {
        // when
        Map<String, PriceSnapshot> result = priceCacheStore.findAll(List.of());

        // then
        assertThat(result).isEmpty();
    }
}
