package com.quantlime.backtest.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.backtest.domain.BacktestAxis;
import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.backtest.domain.BacktestResult;
import com.quantlime.backtest.domain.BacktestSampleSplit;
import com.quantlime.backtest.domain.CrossSectionalBacktestResult;
import com.quantlime.backtest.repository.BacktestDailyScoreRepository;
import com.quantlime.backtest.repository.BacktestResultRepository;
import com.quantlime.backtest.repository.CrossSectionalBacktestResultRepository;
import com.quantlime.stock.domain.MarketType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BacktestAppenderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 30);

    @Mock
    private BacktestResultRepository backtestResultRepository;

    @Mock
    private BacktestDailyScoreRepository backtestDailyScoreRepository;

    @Mock
    private CrossSectionalBacktestResultRepository crossSectionalBacktestResultRepository;

    @InjectMocks
    private BacktestAppender appender;

    private BacktestResult result(String stockCode, double rankIc) {
        return BacktestResult.of(stockCode, BacktestAxis.TREND, 20, "v3.0", DATE, 100, rankIc, 0.0, 0.1,
            0.5, 0.2, List.of());
    }

    @Test
    @DisplayName("[기존 행이 없으면 새로 저장한다]")
    void saveAll_newRow_isSaved() {
        BacktestResult incoming = result("005930", 0.05);
        given(backtestResultRepository.findByStockCodeAndAxisAndHorizonDaysAndScoreVersion(
            "005930", BacktestAxis.TREND, 20, "v3.0")).willReturn(Optional.empty());

        appender.saveAll(List.of(incoming));

        verify(backtestResultRepository).save(incoming);
    }

    @Test
    @DisplayName("[같은 키의 기존 행이 있으면 새 행을 만들지 않고 값을 갱신한다]")
    void saveAll_existingRow_isUpdatedInPlace() {
        BacktestResult existing = result("005930", 0.01);
        BacktestResult incoming = BacktestResult.of("005930", BacktestAxis.TREND, 20, "v3.0", DATE.plusDays(1), 150,
            0.09, 0.02, 0.15, 0.6, 0.3, List.of());
        given(backtestResultRepository.findByStockCodeAndAxisAndHorizonDaysAndScoreVersion(
            "005930", BacktestAxis.TREND, 20, "v3.0")).willReturn(Optional.of(existing));

        appender.saveAll(List.of(incoming));

        assertThat(existing.getRankIc()).isEqualTo(0.09);
        assertThat(existing.getSampleSize()).isEqualTo(150);
        assertThat(existing.getBacktestDate()).isEqualTo(DATE.plusDays(1));
        verify(backtestResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("[한 행의 저장이 실패해도 예외를 전파하지 않고 나머지 행은 저장한다]")
    void saveAll_oneRowFails_otherRowsStillSaved() {
        BacktestResult bad = result("BAD", 0.01);
        BacktestResult good = result("005930", 0.05);
        given(backtestResultRepository.findByStockCodeAndAxisAndHorizonDaysAndScoreVersion(
            "BAD", BacktestAxis.TREND, 20, "v3.0")).willThrow(new IllegalStateException("db"));
        given(backtestResultRepository.findByStockCodeAndAxisAndHorizonDaysAndScoreVersion(
            "005930", BacktestAxis.TREND, 20, "v3.0")).willReturn(Optional.empty());

        assertThatCode(() -> appender.saveAll(List.of(bad, good))).doesNotThrowAnyException();

        verify(backtestResultRepository).save(good);
    }

    @Test
    @DisplayName("[일별 스코어 교체는 기존 행을 먼저 지운 뒤 새로 넣는다]")
    void replaceDailyScores_deletesThenInserts() {
        List<BacktestDailyScore> scores = List.of();

        appender.replaceDailyScores("005930", "v3.0", scores);

        InOrder inOrder = inOrder(backtestDailyScoreRepository);
        inOrder.verify(backtestDailyScoreRepository).deleteByStockCodeAndScoreVersion("005930", "v3.0");
        inOrder.verify(backtestDailyScoreRepository).saveAll(scores);
    }

    private CrossSectionalBacktestResult crossSectional(double meanIc, int stockCount) {
        return CrossSectionalBacktestResult.of(MarketType.KOSPI, BacktestAxis.TREND, 20, "v3.0",
            BacktestSampleSplit.FULL, DATE, stockCount, meanIc, 0.01, 0.09, 250, 200_000,
            null, null, null, null, List.of());
    }

    @Test
    @DisplayName("[횡단면 결과 - 기존 행이 없으면 저장하고, 있으면 값을 갱신한다]")
    void saveCrossSectional_insertsOrUpdates() {
        CrossSectionalBacktestResult fresh = crossSectional(0.05, 900);
        given(crossSectionalBacktestResultRepository
            .findByMarketTypeAndAxisAndHorizonDaysAndScoreVersionAndSampleSplit(
                MarketType.KOSPI, BacktestAxis.TREND, 20, "v3.0", BacktestSampleSplit.FULL))
            .willReturn(Optional.empty());
        appender.saveCrossSectional(fresh);
        verify(crossSectionalBacktestResultRepository).save(fresh);

        CrossSectionalBacktestResult existing = crossSectional(0.01, 800);
        given(crossSectionalBacktestResultRepository
            .findByMarketTypeAndAxisAndHorizonDaysAndScoreVersionAndSampleSplit(
                MarketType.KOSPI, BacktestAxis.TREND, 20, "v3.0", BacktestSampleSplit.FULL))
            .willReturn(Optional.of(existing));
        appender.saveCrossSectional(crossSectional(0.07, 950));

        assertThat(existing.getMeanIc()).isEqualTo(0.07);
        assertThat(existing.getStockCount()).isEqualTo(950);
    }
}
