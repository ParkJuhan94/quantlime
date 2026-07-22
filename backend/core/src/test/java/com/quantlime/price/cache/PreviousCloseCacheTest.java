package com.quantlime.price.cache;

import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.repository.DailyPriceRepository;
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

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PreviousCloseCacheTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @InjectMocks
    private PreviousCloseCache previousCloseCache;

    @Test
    @DisplayName("[첫 조회 시 배치 조회로 전일 종가를 가져온다]")
    void get_firstCall_fetchesBatch() {
        // given
        given(dailyPriceRepository.findLatestBeforeDate(anyList(), any()))
            .willReturn(List.of(dailyPrice(STOCK_CODE, 70000L)));

        // when
        Map<String, Long> result = previousCloseCache.get(List.of(STOCK_CODE));

        // then
        assertThat(result).containsEntry(STOCK_CODE, 70000L);
        verify(dailyPriceRepository, times(1)).findLatestBeforeDate(anyList(), any());
    }

    @Test
    @DisplayName("[같은 날 같은 종목을 다시 조회하면 배치 조회를 반복하지 않는다]")
    void get_sameDaySameCodes_doesNotRefetch() {
        // given
        given(dailyPriceRepository.findLatestBeforeDate(anyList(), any()))
            .willReturn(List.of(dailyPrice(STOCK_CODE, 70000L)));

        // when
        previousCloseCache.get(List.of(STOCK_CODE));
        previousCloseCache.get(List.of(STOCK_CODE));

        // then
        verify(dailyPriceRepository, times(1)).findLatestBeforeDate(anyList(), any());
    }

    @Test
    @DisplayName("[캐시에 없는 신규 종목이 섞이면 그 시점에 다시 조회한다]")
    void get_newCodeNotCached_refetches() {
        // given
        String newCode = "000660";
        given(dailyPriceRepository.findLatestBeforeDate(anyList(), any()))
            .willReturn(List.of(dailyPrice(STOCK_CODE, 70000L)))
            .willReturn(List.of(dailyPrice(STOCK_CODE, 70000L), dailyPrice(newCode, 50000L)));

        // when: 처음엔 기존 종목만, 두 번째엔 캐시에 없는 신규 종목이 섞임
        previousCloseCache.get(List.of(STOCK_CODE));
        Map<String, Long> result = previousCloseCache.get(List.of(STOCK_CODE, newCode));

        // then
        assertThat(result).containsEntry(newCode, 50000L);
        verify(dailyPriceRepository, times(2)).findLatestBeforeDate(anyList(), any());
    }

    @Test
    @DisplayName("[날짜가 바뀌면 같은 종목이어도 다시 조회한다]")
    void get_dateChanged_refetches() {
        // given
        given(dailyPriceRepository.findLatestBeforeDate(anyList(), any()))
            .willReturn(List.of(dailyPrice(STOCK_CODE, 70000L)))
            .willReturn(List.of(dailyPrice(STOCK_CODE, 71000L)));
        previousCloseCache.get(List.of(STOCK_CODE));

        // when: 캐시된 날짜를 어제로 되돌려 "날짜가 바뀐" 상태를 재현
        ReflectionTestUtils.setField(previousCloseCache, "cachedDate", LocalDate.now().minusDays(1));
        Map<String, Long> result = previousCloseCache.get(List.of(STOCK_CODE));

        // then
        assertThat(result).containsEntry(STOCK_CODE, 71000L);
        verify(dailyPriceRepository, times(2)).findLatestBeforeDate(anyList(), any());
    }

    private DailyPrice dailyPrice(String stockCode, long closePrice) {
        return DailyPrice.of(stockCode, LocalDate.now().minusDays(1),
            closePrice, closePrice, closePrice, closePrice, 1000000L);
    }
}
