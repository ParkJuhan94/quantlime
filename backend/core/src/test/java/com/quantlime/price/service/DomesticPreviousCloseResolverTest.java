package com.quantlime.price.service;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.DomesticRegularClosePrice;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.DomesticRegularClosePriceRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DomesticPreviousCloseResolverTest {

    private static final String NXT_STOCK = "005930";
    private static final String NON_NXT_STOCK = "000660";

    @Mock
    private DomesticRegularClosePriceRepository domesticRegularClosePriceRepository;

    @Mock
    private DomesticDailyPriceRepository domesticDailyPriceRepository;

    @InjectMocks
    private DomesticPreviousCloseResolver domesticPreviousCloseResolver;

    @Test
    @DisplayName("[정규장 종가 캡처가 있으면 일봉 종가를 조회하지 않고 그 값을 그대로 쓴다]")
    void resolve_regularCloseCapturedForAll_neverFallsBackToDailyCandle() {
        // given
        LocalDate date = LocalDate.of(2026, 8, 17);
        given(domesticRegularClosePriceRepository.findLatestBeforeDate(List.of(NXT_STOCK), date))
            .willReturn(List.of(DomesticRegularClosePrice.of(NXT_STOCK, date.minusDays(1), 70000L)));

        // when
        Map<String, Double> result = domesticPreviousCloseResolver.resolve(List.of(NXT_STOCK), date);

        // then
        assertThat(result).containsEntry(NXT_STOCK, 70000.0);
        verify(domesticDailyPriceRepository, org.mockito.Mockito.never())
            .findLatestBeforeDate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("[정규장 종가 캡처가 없는 종목만 일봉(NXT 반영) 종가로 폴백한다]")
    void resolve_someStocksMissingRegularClose_fallsBackOnlyForThoseStocks() {
        // given: NXT_STOCK은 정규장 종가 캡처가 있고, NON_NXT_STOCK은 캡처가 없어(과거
        // 데이터/캡처 실패일 등) 일봉 종가로 폴백해야 하는 상황을 재현.
        LocalDate date = LocalDate.of(2026, 8, 17);
        given(domesticRegularClosePriceRepository.findLatestBeforeDate(
            List.of(NXT_STOCK, NON_NXT_STOCK), date))
            .willReturn(List.of(DomesticRegularClosePrice.of(NXT_STOCK, date.minusDays(1), 70000L)));
        given(domesticDailyPriceRepository.findLatestBeforeDate(List.of(NON_NXT_STOCK), date))
            .willReturn(List.of(DomesticDailyPrice.of(
                NON_NXT_STOCK, date.minusDays(1), 50000L, 51000L, 49000L, 50500L, 900L)));

        // when
        Map<String, Double> result = domesticPreviousCloseResolver.resolve(
            List.of(NXT_STOCK, NON_NXT_STOCK), date);

        // then
        assertThat(result).containsEntry(NXT_STOCK, 70000.0);
        assertThat(result).containsEntry(NON_NXT_STOCK, 50500.0);
    }

    @Test
    @DisplayName("[정규장 종가 캡처가 하나도 없으면 전부 일봉 종가로 폴백한다]")
    void resolve_noRegularCloseAtAll_fallsBackToDailyCandleForAll() {
        // given
        LocalDate date = LocalDate.of(2026, 8, 17);
        given(domesticRegularClosePriceRepository.findLatestBeforeDate(List.of(NXT_STOCK), date))
            .willReturn(List.of());
        given(domesticDailyPriceRepository.findLatestBeforeDate(List.of(NXT_STOCK), date))
            .willReturn(List.of(DomesticDailyPrice.of(
                NXT_STOCK, date.minusDays(1), 69000L, 70500L, 68500L, 70200L, 1200L)));

        // when
        Map<String, Double> result = domesticPreviousCloseResolver.resolve(List.of(NXT_STOCK), date);

        // then
        assertThat(result).containsEntry(NXT_STOCK, 70200.0);
    }

    @Test
    @DisplayName("[빈 종목 목록이면 조회 없이 빈 맵을 반환한다]")
    void resolve_emptyStockCodes_returnsEmptyMapWithoutQuerying() {
        // when
        Map<String, Double> result = domesticPreviousCloseResolver.resolve(List.of(), LocalDate.now());

        // then
        assertThat(result).isEmpty();
        verify(domesticRegularClosePriceRepository, org.mockito.Mockito.never())
            .findLatestBeforeDate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
