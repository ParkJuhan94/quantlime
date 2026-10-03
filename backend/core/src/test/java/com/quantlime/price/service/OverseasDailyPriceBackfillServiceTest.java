package com.quantlime.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.quantlime.price.implement.OverseasDailyPriceCollector;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 재백필 구간 계산 같은 정책만 검증한다 - 수집·저장 자체는 OverseasDailyPriceCollectorTest. */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OverseasDailyPriceBackfillServiceTest {

    private static final String STOCK_CODE = "AAPL";

    @Mock
    private OverseasDailyPriceCollector overseasDailyPriceCollector;

    @InjectMocks
    private OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;

    @Test
    @DisplayName("[진입점은 Collector로 그대로 위임한다]")
    void entryPoints_delegateToCollector() {
        overseasDailyPriceBackfillService.refreshRecent(STOCK_CODE, 8);
        overseasDailyPriceBackfillService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        verify(overseasDailyPriceCollector).refreshRecent(STOCK_CODE, 8);
        verify(overseasDailyPriceCollector).backfillHistory(STOCK_CODE, 200);
    }

    @Test
    @DisplayName("[수정주가 전 구간 재백필은 400일 목표로 위임한다]")
    void rebackfillAdjustedHistory_full_uses400Days() {
        given(overseasDailyPriceCollector.rebackfill(eq(STOCK_CODE), anyInt())).willReturn(2);

        int created = overseasDailyPriceBackfillService.rebackfillAdjustedHistory(STOCK_CODE);

        assertThat(created).isEqualTo(2);
        verify(overseasDailyPriceCollector).rebackfill(STOCK_CODE, 400);
    }

    @Test
    @DisplayName("[from 지정 재백필은 달력일 갭에 여유 5일을 더한 개수만 요청한다]")
    void rebackfillAdjustedHistory_from_addsBufferToCalendarGap() {
        LocalDate from = LocalDate.now().minusDays(30);

        overseasDailyPriceBackfillService.rebackfillAdjustedHistory(STOCK_CODE, from);

        ArgumentCaptor<Integer> target = ArgumentCaptor.forClass(Integer.class);
        verify(overseasDailyPriceCollector).rebackfill(eq(STOCK_CODE), target.capture());
        assertThat(target.getValue()).isEqualTo((int) ChronoUnit.DAYS.between(from, LocalDate.now()) + 5);
    }
}
