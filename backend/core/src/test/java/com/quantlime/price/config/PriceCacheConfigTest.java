package com.quantlime.price.config;

import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

/**
 * {@link PreviousCloseCache} 통합(2026-08-01) 이후, 국내/해외 각각의 조회
 * 로직(리포지토리 타입·값 변환)이 정확히 흡수됐는지 검증한다 - 캐싱 제어
 * 흐름 자체는 {@code PreviousCloseCacheTest}가 이미 다루므로 여기서는
 * fetcher 람다의 동작만 확인한다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PriceCacheConfigTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private DomesticDailyPriceRepository domesticDailyPriceRepository;

    @Mock
    private OverseasDailyPriceRepository overseasDailyPriceRepository;

    private final PriceCacheConfig priceCacheConfig = new PriceCacheConfig();

    @Test
    @DisplayName("[국내 fetcher는 원화 종가(Long)를 Double로 변환한다]")
    void domesticPreviousCloseCache_convertsLongToDouble() {
        // given
        given(domesticDailyPriceRepository.findLatestBeforeDate(anyList(), any())).willReturn(
            List.of(DomesticDailyPrice.of(STOCK_CODE, LocalDate.now().minusDays(1), 70000L, 70000L, 70000L, 70000L, 1_000_000L)));
        PreviousCloseCache cache = priceCacheConfig.domesticPreviousCloseCache(domesticDailyPriceRepository);

        // when
        Map<String, Double> result = cache.get(List.of(STOCK_CODE));

        // then
        assertThat(result).containsEntry(STOCK_CODE, 70000.0);
    }

    @Test
    @DisplayName("[해외 fetcher는 달러 종가(Double)를 그대로 반환한다]")
    void overseasPreviousCloseCache_passesThroughDouble() {
        // given
        given(overseasDailyPriceRepository.findLatestBeforeDate(anyList(), any())).willReturn(
            List.of(OverseasDailyPrice.of("AAPL", LocalDate.now().minusDays(1), 185.70, 185.70, 185.70, 185.70, 1_000_000L)));
        PreviousCloseCache cache = priceCacheConfig.overseasPreviousCloseCache(overseasDailyPriceRepository);

        // when
        Map<String, Double> result = cache.get(List.of("AAPL"));

        // then
        assertThat(result).containsEntry("AAPL", 185.70);
    }
}
