package com.quantlime.price.cache;

import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** {@link PreviousCloseCache}(국내)와 동일한 시나리오를 해외(Double)로 검증한다. */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OverseasPreviousCloseCacheTest {

    private static final String STOCK_CODE = "AAPL";

    @Mock
    private OverseasDailyPriceRepository overseasDailyPriceRepository;

    @InjectMocks
    private OverseasPreviousCloseCache overseasPreviousCloseCache;

    @Test
    @DisplayName("[첫 조회 시 배치 조회로 전일 종가를 가져온다]")
    void get_firstCall_fetchesBatch() {
        // given
        given(overseasDailyPriceRepository.findLatestBeforeDate(anyList(), any()))
            .willReturn(List.of(overseasDailyPrice(STOCK_CODE, 185.70)));

        // when
        Map<String, Double> result = overseasPreviousCloseCache.get(List.of(STOCK_CODE));

        // then
        assertThat(result).containsEntry(STOCK_CODE, 185.70);
        verify(overseasDailyPriceRepository, times(1)).findLatestBeforeDate(anyList(), any());
    }

    @Test
    @DisplayName("[같은 날 같은 종목을 다시 조회하면 배치 조회를 반복하지 않는다]")
    void get_sameDaySameCodes_doesNotRefetch() {
        // given
        given(overseasDailyPriceRepository.findLatestBeforeDate(anyList(), any()))
            .willReturn(List.of(overseasDailyPrice(STOCK_CODE, 185.70)));

        // when
        overseasPreviousCloseCache.get(List.of(STOCK_CODE));
        overseasPreviousCloseCache.get(List.of(STOCK_CODE));

        // then
        verify(overseasDailyPriceRepository, times(1)).findLatestBeforeDate(anyList(), any());
    }

    @Test
    @DisplayName("[캐시에 없는 신규 종목이 섞이면 그 시점에 다시 조회한다]")
    void get_newCodeNotCached_refetches() {
        // given
        String newCode = "MSFT";
        given(overseasDailyPriceRepository.findLatestBeforeDate(anyList(), any()))
            .willReturn(List.of(overseasDailyPrice(STOCK_CODE, 185.70)))
            .willReturn(List.of(overseasDailyPrice(STOCK_CODE, 185.70), overseasDailyPrice(newCode, 397.0)));

        // when
        overseasPreviousCloseCache.get(List.of(STOCK_CODE));
        Map<String, Double> result = overseasPreviousCloseCache.get(List.of(STOCK_CODE, newCode));

        // then
        assertThat(result).containsEntry(newCode, 397.0);
        verify(overseasDailyPriceRepository, times(2)).findLatestBeforeDate(anyList(), any());
    }

    @Test
    @DisplayName("[날짜가 바뀌면 같은 종목이어도 다시 조회한다]")
    void get_dateChanged_refetches() {
        // given
        given(overseasDailyPriceRepository.findLatestBeforeDate(anyList(), any()))
            .willReturn(List.of(overseasDailyPrice(STOCK_CODE, 185.70)))
            .willReturn(List.of(overseasDailyPrice(STOCK_CODE, 190.10)));
        overseasPreviousCloseCache.get(List.of(STOCK_CODE));

        // when
        ReflectionTestUtils.setField(overseasPreviousCloseCache, "cachedDate", LocalDate.now().minusDays(1));
        Map<String, Double> result = overseasPreviousCloseCache.get(List.of(STOCK_CODE));

        // then
        assertThat(result).containsEntry(STOCK_CODE, 190.10);
        verify(overseasDailyPriceRepository, times(2)).findLatestBeforeDate(anyList(), any());
    }

    private OverseasDailyPrice overseasDailyPrice(String stockCode, double closePrice) {
        return OverseasDailyPrice.of(stockCode, LocalDate.now().minusDays(1),
            closePrice, closePrice, closePrice, closePrice, 1_000_000L);
    }
}
