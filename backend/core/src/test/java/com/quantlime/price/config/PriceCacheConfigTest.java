package com.quantlime.price.config;

import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.price.service.DomesticPreviousCloseResolver;
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
    private DomesticPreviousCloseResolver domesticPreviousCloseResolver;

    @Mock
    private OverseasDailyPriceRepository overseasDailyPriceRepository;

    private final PriceCacheConfig priceCacheConfig = new PriceCacheConfig();

    @Test
    @DisplayName("[국내 fetcher는 DomesticPreviousCloseResolver에 그대로 위임한다]")
    void domesticPreviousCloseCache_delegatesToResolver() {
        // given: 정규장 종가/일봉 폴백 조합 로직 자체는 DomesticPreviousCloseResolverTest가
        // 다루므로, 여기서는 fetcher 람다가 그 resolver를 그대로 호출하는지만 확인한다
        // (NXT 애프터마켓 종가 오염 문제로 국내만 resolver를 거치게 된 배경은
        // DomesticRegularClosePrice 클래스 javadoc 참고).
        given(domesticPreviousCloseResolver.resolve(anyList(), any())).willReturn(Map.of(STOCK_CODE, 70000.0));
        PreviousCloseCache cache = priceCacheConfig.domesticPreviousCloseCache(domesticPreviousCloseResolver);

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
