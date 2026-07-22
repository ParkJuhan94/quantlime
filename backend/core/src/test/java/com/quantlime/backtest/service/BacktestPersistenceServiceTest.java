package com.quantlime.backtest.service;

import com.quantlime.backtest.domain.BacktestAxis;
import com.quantlime.backtest.domain.BacktestBucket;
import com.quantlime.backtest.domain.BacktestResult;
import com.quantlime.backtest.repository.BacktestResultRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BacktestPersistenceServiceTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private BacktestResultRepository backtestResultRepository;

    @InjectMocks
    private BacktestPersistenceService backtestPersistenceService;

    @Test
    @DisplayName("[해당 (축, horizon, 버전) 조합이 없으면 새로 저장한다]")
    void saveAll_noExistingRow_savesNew() {
        // given
        given(backtestResultRepository
            .findByStockCodeAndAxisAndHorizonDaysAndScoreVersion(eq(STOCK_CODE), any(), any(int.class), any()))
            .willReturn(Optional.empty());

        // when
        backtestPersistenceService.saveAll(List.of(result(5, 0.1)));

        // then
        verify(backtestResultRepository).save(any(BacktestResult.class));
    }

    @Test
    @DisplayName("[같은 (축, horizon, 버전) 조합이 이미 있으면 기존 행을 갱신하고 새로 저장하지 않는다]")
    void saveAll_existingRow_updatesInPlaceWithoutSave() {
        // given
        BacktestResult existing = BacktestResult.of(
            STOCK_CODE, BacktestAxis.TREND, 5, "v2.1", LocalDate.now().minusDays(1),
            300, 0.05, null, null, 0.1, 0.2, List.of());
        given(backtestResultRepository
            .findByStockCodeAndAxisAndHorizonDaysAndScoreVersion(STOCK_CODE, BacktestAxis.TREND, 5, "v2.1"))
            .willReturn(Optional.of(existing));

        // when
        backtestPersistenceService.saveAll(List.of(result(5, 0.3)));

        // then
        assertThat(existing.getRankIc()).isEqualTo(0.3);
        verify(backtestResultRepository, never()).save(any(BacktestResult.class));
    }

    @Test
    @DisplayName("[한 행의 저장이 실패해도 나머지 행은 계속 저장된다]")
    void saveAll_oneRowFails_othersStillPersisted() {
        // given
        given(backtestResultRepository
            .findByStockCodeAndAxisAndHorizonDaysAndScoreVersion(eq(STOCK_CODE), any(), any(int.class), any()))
            .willReturn(Optional.empty());
        given(backtestResultRepository.save(any(BacktestResult.class)))
            .willAnswer(invocation -> {
                BacktestResult result = invocation.getArgument(0);
                if (result.getHorizonDays() == 10) {
                    throw new RuntimeException("저장 실패");
                }
                return result;
            });

        // when: 예외가 전파되지 않아야 함
        backtestPersistenceService.saveAll(List.of(result(5, 0.1), result(10, 0.2), result(20, 0.3)));

        // then: 3건 모두 저장 시도(10일은 실패하지만 5/20일은 성공)
        verify(backtestResultRepository, times(3)).save(any(BacktestResult.class));
    }

    private BacktestResult result(int horizonDays, Double rankIc) {
        return BacktestResult.of(
            STOCK_CODE, BacktestAxis.TREND, horizonDays, "v2.1", LocalDate.now(),
            300, rankIc, -0.1, 0.1, 0.05, 0.1,
            List.of(BacktestBucket.of(1, 0.01, 0.01, 0.5, 60)));
    }
}
