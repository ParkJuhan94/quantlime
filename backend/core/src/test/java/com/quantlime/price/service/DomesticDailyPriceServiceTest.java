package com.quantlime.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.implement.DailyPriceReader;
import com.quantlime.price.implement.DomesticDailyPriceCollector;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 기본 목표·조회 구간 같은 정책만 검증한다 - 수집·저장 자체는 DomesticDailyPriceCollectorTest. */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DomesticDailyPriceServiceTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private DailyPriceReader dailyPriceReader;

    @Mock
    private DomesticDailyPriceCollector domesticDailyPriceCollector;

    @InjectMocks
    private DomesticDailyPriceService domesticDailyPriceService;

    @Test
    @DisplayName("[refreshRecent 기본형은 최근 10일 조회 구간으로 Collector에 위임한다]")
    void refreshRecent_usesDefaultLookback() {
        domesticDailyPriceService.refreshRecent(STOCK_CODE);

        verify(domesticDailyPriceCollector).refreshRecent(STOCK_CODE, 10);
    }

    @Test
    @DisplayName("[기본 백필 목표는 200일이다]")
    void backfillHistoryIfNeeded_defaultTargetIs200() {
        domesticDailyPriceService.backfillHistoryIfNeeded(STOCK_CODE);

        verify(domesticDailyPriceCollector).backfillHistory(STOCK_CODE, 200);
    }

    @Test
    @DisplayName("[수정주가 전 구간 재백필은 400일 목표로 위임한다]")
    void rebackfillAdjustedHistory_full_uses400Days() {
        given(domesticDailyPriceCollector.rebackfill(eq(STOCK_CODE), anyInt())).willReturn(3);

        int created = domesticDailyPriceService.rebackfillAdjustedHistory(STOCK_CODE);

        assertThat(created).isEqualTo(3);
        verify(domesticDailyPriceCollector).rebackfill(STOCK_CODE, 400);
    }

    @Test
    @DisplayName("[from 지정 재백필은 달력일 갭에 여유 5일을 더한 개수만 요청한다]")
    void rebackfillAdjustedHistory_from_addsBufferToCalendarGap() {
        LocalDate from = LocalDate.now().minusDays(30);

        domesticDailyPriceService.rebackfillAdjustedHistory(STOCK_CODE, from);

        ArgumentCaptor<Integer> target = ArgumentCaptor.forClass(Integer.class);
        verify(domesticDailyPriceCollector).rebackfill(eq(STOCK_CODE), target.capture());
        assertThat(target.getValue()).isEqualTo((int) ChronoUnit.DAYS.between(from, LocalDate.now()) + 5);
    }

    @Test
    @DisplayName("[종목코드 목록이 비어 있으면 조회하지 않고 빈 리스트를 반환한다]")
    void getDailyPrices_emptyCodes_returnsEmptyWithoutQuery() {
        List<DomesticDailyPrice> result =
            domesticDailyPriceService.getDailyPrices(List.of(), LocalDate.now().minusDays(5), LocalDate.now());

        assertThat(result).isEmpty();
    }
}
