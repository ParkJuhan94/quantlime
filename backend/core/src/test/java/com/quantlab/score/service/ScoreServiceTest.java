package com.quantlab.score.service;

import com.quantlab.common.exception.ExternalApiException;
import com.quantlab.common.exception.NotFoundException;
import com.quantlab.infra.python.PythonEngineClient;
import com.quantlab.infra.python.dto.ScoreBatchApiRequest;
import com.quantlab.infra.python.dto.ScoreBatchApiResponse;
import com.quantlab.infra.python.dto.ScoreBatchApiResponse.DivergenceApiResponse;
import com.quantlab.infra.python.dto.ScoreBatchApiResponse.StockScoreApiResponse;
import com.quantlab.infra.python.exception.PythonEngineErrorCode;
import com.quantlab.price.domain.DailyPrice;
import com.quantlab.price.service.DailyPriceService;
import com.quantlab.score.domain.Score;
import com.quantlab.score.dto.response.ScoreResponse;
import com.quantlab.score.repository.ScoreRepository;
import com.quantlab.watchlist.repository.WatchlistRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private DailyPriceService dailyPriceService;

    @Mock
    private PythonEngineClient pythonEngineClient;

    @Mock
    private ScoreRepository scoreRepository;

    @Mock
    private WatchlistRepository watchlistRepository;

    @InjectMocks
    private ScoreService scoreService;

    @Test
    @DisplayName("[OHLCV 이력이 없으면 퀀트 엔진을 호출하지 않는다]")
    void recalculateScore_noDailyPrices_skipsPythonCall() {
        // given
        given(dailyPriceService.getDailyPrices(anyString(), any(), any()))
            .willReturn(List.of());

        // when
        scoreService.recalculateScore(STOCK_CODE);

        // then
        verify(pythonEngineClient, never()).calculateScoreBatch(any());
    }

    @Test
    @DisplayName("[해당 날짜의 스코어가 없으면 새로 저장한다]")
    void recalculateScore_noExistingScoreToday_savesNewScore() {
        // given
        given(dailyPriceService.getDailyPrices(anyString(), any(), any()))
            .willReturn(List.of(dailyPrice(LocalDate.of(2026, 7, 3))));
        given(pythonEngineClient.calculateScoreBatch(any(ScoreBatchApiRequest.class)))
            .willReturn(new ScoreBatchApiResponse(List.of(successResponse(STOCK_CODE, 82.0))));
        given(scoreRepository.findByStockCodeAndScoreDate(STOCK_CODE, LocalDate.now()))
            .willReturn(Optional.empty());

        // when
        scoreService.recalculateScore(STOCK_CODE);

        // then
        verify(scoreRepository).save(any(Score.class));
    }

    @Test
    @DisplayName("[같은 날 재계산이면 기존 행을 갱신하고 새로 저장하지 않는다]")
    void recalculateScore_existingScoreToday_updatesInPlaceWithoutSave() {
        // given
        Score existing = Score.of(STOCK_CODE, LocalDate.now(), 50.0, 50.0, 50.0,
            null, false, null, "이전 코멘트", false);
        given(dailyPriceService.getDailyPrices(anyString(), any(), any()))
            .willReturn(List.of(dailyPrice(LocalDate.of(2026, 7, 3))));
        given(pythonEngineClient.calculateScoreBatch(any(ScoreBatchApiRequest.class)))
            .willReturn(new ScoreBatchApiResponse(List.of(successResponse(STOCK_CODE, 91.5))));
        given(scoreRepository.findByStockCodeAndScoreDate(STOCK_CODE, LocalDate.now()))
            .willReturn(Optional.of(existing));

        // when
        scoreService.recalculateScore(STOCK_CODE);

        // then: 기존 엔티티가 새 값으로 갱신되고, 새 행은 저장되지 않는다
        assertThat(existing.getCompositeScore()).isEqualTo(91.5);
        verify(scoreRepository, never()).save(any(Score.class));
    }

    @Test
    @DisplayName("[퀀트 엔진 호출이 실패하면 예외가 그대로 전파되고 DB는 변경되지 않는다]")
    void recalculateScore_pythonEngineFails_propagatesAndLeavesHistoryUntouched() {
        // given
        given(dailyPriceService.getDailyPrices(anyString(), any(), any()))
            .willReturn(List.of(dailyPrice(LocalDate.of(2026, 7, 3))));
        given(pythonEngineClient.calculateScoreBatch(any(ScoreBatchApiRequest.class)))
            .willThrow(new ExternalApiException(PythonEngineErrorCode.SCORE_CALCULATION_FAILED));

        // when & then: 예외는 상위(WatchlistService/스케줄러)에서 잡으므로 여기선 전파돼야 함
        assertThatThrownBy(() -> scoreService.recalculateScore(STOCK_CODE))
            .isInstanceOf(ExternalApiException.class);
        verify(scoreRepository, never()).save(any(Score.class));
    }

    @Test
    @DisplayName("[관심 종목이 없으면 일괄 재계산 시 퀀트 엔진을 호출하지 않는다]")
    void recalculateWatchlistedScores_noWatchlist_skipsPythonCall() {
        // given
        given(watchlistRepository.findDistinctStockCodes()).willReturn(List.of());

        // when
        scoreService.recalculateWatchlistedScores();

        // then
        verify(pythonEngineClient, never()).calculateScoreBatch(any());
    }

    @Test
    @DisplayName("[관심 종목이 있으면 일괄 조회 후 결과를 모두 저장한다]")
    void recalculateWatchlistedScores_withWatchlist_savesAllResults() {
        // given
        String secondCode = "000660";
        given(watchlistRepository.findDistinctStockCodes())
            .willReturn(List.of(STOCK_CODE, secondCode));
        given(dailyPriceService.getDailyPrices(anyString(), any(), any()))
            .willReturn(List.of(dailyPrice(LocalDate.of(2026, 7, 3))));
        given(pythonEngineClient.calculateScoreBatch(any(ScoreBatchApiRequest.class)))
            .willReturn(new ScoreBatchApiResponse(List.of(
                successResponse(STOCK_CODE, 70.0), successResponse(secondCode, 60.0))));
        given(scoreRepository.findByStockCodeAndScoreDate(anyString(), any()))
            .willReturn(Optional.empty());

        // when
        scoreService.recalculateWatchlistedScores();

        // then
        verify(scoreRepository, org.mockito.Mockito.times(2)).save(any(Score.class));
    }

    @Test
    @DisplayName("[저장된 스코어가 있으면 조회한다]")
    void getScore_found_returnsResponse() {
        // given
        Score score = Score.of(STOCK_CODE, LocalDate.now(), 80.0, 40.0, 65.0,
            null, false, null, "코멘트", false);
        given(scoreRepository.findTopByStockCodeOrderByScoreDateDesc(STOCK_CODE))
            .willReturn(Optional.of(score));

        // when
        ScoreResponse response = scoreService.getScore(STOCK_CODE);

        // then
        assertThat(response.stockCode()).isEqualTo(STOCK_CODE);
        assertThat(response.compositeScore()).isEqualTo(65.0);
    }

    @Test
    @DisplayName("[저장된 스코어가 없으면 예외가 발생한다]")
    void getScore_notFound_throwsNotFoundException() {
        // given
        given(scoreRepository.findTopByStockCodeOrderByScoreDateDesc(STOCK_CODE))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> scoreService.getScore(STOCK_CODE))
            .isInstanceOf(NotFoundException.class);
    }

    private DailyPrice dailyPrice(LocalDate tradeDate) {
        return DailyPrice.of(STOCK_CODE, tradeDate, 70000L, 71000L, 69000L, 70500L, 1000000L);
    }

    private StockScoreApiResponse successResponse(String stockCode, double compositeScore) {
        return new StockScoreApiResponse(
            stockCode, 70.0, 50.0, compositeScore, "A",
            new DivergenceApiResponse(false, null), "코멘트", false);
    }
}
