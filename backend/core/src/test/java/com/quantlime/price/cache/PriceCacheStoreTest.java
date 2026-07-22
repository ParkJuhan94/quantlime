package com.quantlime.price.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.price.dto.response.PriceSnapshot;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PriceCacheStoreTest {

    private static final String STOCK_CODE = "005930";

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
            new PriceSnapshot(STOCK_CODE, 70000L, 1.5, "2026-07-06T09:00:00+09:00");
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
}
