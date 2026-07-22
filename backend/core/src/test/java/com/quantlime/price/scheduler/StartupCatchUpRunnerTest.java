package com.quantlime.price.scheduler;

import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.repository.DailyPriceRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StartupCatchUpRunnerTest {

    private static final String ANCHOR_STOCK_CODE = "005930";

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @Mock
    private OhlcvCollectorScheduler ohlcvCollectorScheduler;

    @Mock
    private TaskExecutor startupCatchUpTaskExecutor;

    @InjectMocks
    private StartupCatchUpRunner startupCatchUpRunner;

    @Test
    @DisplayName("[최근 데이터가 있으면 캐치업 배치를 트리거하지 않는다]")
    void run_recentData_doesNotTriggerCatchUp() throws Exception {
        // given
        given(dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(ANCHOR_STOCK_CODE))
            .willReturn(Optional.of(dailyPrice(LocalDate.now().minusDays(1))));

        // when
        startupCatchUpRunner.run(null);

        // then
        verify(startupCatchUpTaskExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("[데이터가 오래됐으면(4일 초과) 캐치업 배치를 비동기로 트리거한다]")
    void run_staleData_triggersCatchUpAsynchronously() throws Exception {
        // given
        given(dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(ANCHOR_STOCK_CODE))
            .willReturn(Optional.of(dailyPrice(LocalDate.now().minusDays(10))));

        // when
        startupCatchUpRunner.run(null);

        // then: 실제로 실행기에 넘겨진 작업을 직접 실행해 OHLCV 수집이 호출되는지까지 확인
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(startupCatchUpTaskExecutor).execute(taskCaptor.capture());
        taskCaptor.getValue().run();
        verify(ohlcvCollectorScheduler).collectDailyOhlcv();
    }

    @Test
    @DisplayName("[기준 종목의 OHLCV 데이터가 아예 없으면 캐치업 배치를 트리거한다]")
    void run_noData_triggersCatchUp() throws Exception {
        // given
        given(dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(ANCHOR_STOCK_CODE))
            .willReturn(Optional.empty());

        // when
        startupCatchUpRunner.run(null);

        // then
        verify(startupCatchUpTaskExecutor).execute(any());
    }

    @Test
    @DisplayName("[캐치업 배치 실행 중 예외가 나도 상위로 전파되지 않는다]")
    void run_catchUpTaskThrows_doesNotPropagate() throws Exception {
        // given
        given(dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(ANCHOR_STOCK_CODE))
            .willReturn(Optional.empty());
        doThrow(new RuntimeException("boom")).when(ohlcvCollectorScheduler).collectDailyOhlcv();

        // when
        startupCatchUpRunner.run(null);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(startupCatchUpTaskExecutor).execute(taskCaptor.capture());

        // then: SafeExecutor로 감싸져 있어 예외가 밖으로 새지 않아야 한다
        taskCaptor.getValue().run();
    }

    private DailyPrice dailyPrice(LocalDate tradeDate) {
        return DailyPrice.of(ANCHOR_STOCK_CODE, tradeDate, 70000L, 71000L, 69000L, 70500L, 1000000L);
    }
}
