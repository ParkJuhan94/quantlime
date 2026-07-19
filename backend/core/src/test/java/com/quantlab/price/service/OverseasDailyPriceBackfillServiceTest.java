package com.quantlab.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlab.infra.kis.KisApiClient;
import com.quantlab.infra.kis.dto.KisOverseasDailyPriceResponse;
import com.quantlab.infra.kis.dto.KisOverseasDailyPriceResponse.Candle;
import com.quantlab.price.repository.OverseasDailyPriceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OverseasDailyPriceBackfillServiceTest {

    private static final String STOCK_CODE = "AAPL";
    private static final String EXCHANGE_CODE = "NAS";

    @Mock
    private OverseasDailyPriceRepository overseasDailyPriceRepository;

    @Mock
    private KisApiClient kisApiClient;

    @InjectMocks
    private OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;

    @Test
    @DisplayName("[기존 이력이 목표 일수 이상이면 API를 호출하지 않는다]")
    void backfillHistoryIfNeeded_alreadySufficient_doesNotCallApi() {
        // given
        given(overseasDailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(400L);

        // when
        overseasDailyPriceBackfillService.backfillHistoryIfNeeded(STOCK_CODE, EXCHANGE_CODE, 400);

        // then
        verify(kisApiClient, never()).getOverseasDailyPrice(any(), any(), any());
    }

    @Test
    @DisplayName("[짧은 페이지(마지막 페이지)를 받으면 백필을 종료한다]")
    void backfillHistoryIfNeeded_shortPage_stopsAfterFirstPage() {
        // given
        given(overseasDailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        given(kisApiClient.getOverseasDailyPrice(eq(EXCHANGE_CODE), eq(STOCK_CODE), isNull()))
            .willReturn(page(10, "20260716"));
        given(overseasDailyPriceRepository.existsByStockCodeAndTradeDate(eq(STOCK_CODE), any()))
            .willReturn(false);

        // when
        overseasDailyPriceBackfillService.backfillHistoryIfNeeded(STOCK_CODE, EXCHANGE_CODE, 400);

        // then: 10건(<100)만 받았으니 다음 페이지는 호출하지 않는다
        verify(kisApiClient, times(1)).getOverseasDailyPrice(any(), any(), any());
        verify(overseasDailyPriceRepository, times(10)).save(any());
    }

    @Test
    @DisplayName("[100건 꽉 찬 페이지를 받으면 최고령일의 하루 전을 BYMD로 다음 페이지를 요청한다]")
    void backfillHistoryIfNeeded_fullPage_requestsNextPageWithPreviousDay() {
        // given
        LocalDate firstPageStart = LocalDate.parse("20260716", java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        LocalDate firstPageOldest = firstPageStart.minusDays(99);
        String expectedNextBymd = firstPageOldest.minusDays(1)
            .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        given(overseasDailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        given(kisApiClient.getOverseasDailyPrice(eq(EXCHANGE_CODE), eq(STOCK_CODE), isNull()))
            .willReturn(page(100, "20260716"));
        given(kisApiClient.getOverseasDailyPrice(eq(EXCHANGE_CODE), eq(STOCK_CODE), eq(expectedNextBymd)))
            .willReturn(page(50, expectedNextBymd));
        given(overseasDailyPriceRepository.existsByStockCodeAndTradeDate(eq(STOCK_CODE), any()))
            .willReturn(false);

        // when
        overseasDailyPriceBackfillService.backfillHistoryIfNeeded(STOCK_CODE, EXCHANGE_CODE, 130);

        // then: 100건 받은 뒤 최고령일 하루 전을 BYMD로 다음 페이지 요청,
        // 두번째 페이지 50건까지 합쳐 목표(130) 이상이라 거기서 종료
        verify(kisApiClient, times(2)).getOverseasDailyPrice(any(), any(), any());
        verify(overseasDailyPriceRepository, times(150)).save(any());
    }

    private KisOverseasDailyPriceResponse page(int size, String startDate) {
        LocalDate start = LocalDate.parse(startDate, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        List<Candle> candles = IntStream.range(0, size)
            .mapToObj(i -> new Candle(
                start.minusDays(i).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                "150.00", "148.00", "152.00", "147.00", "1000000"))
            .toList();
        return new KisOverseasDailyPriceResponse("0", "OK", candles);
    }
}
