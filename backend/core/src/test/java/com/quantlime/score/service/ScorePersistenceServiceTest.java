package com.quantlime.score.service;

import com.quantlime.infra.python.dto.ScoreBatchApiResponse.DivergenceApiResponse;
import com.quantlime.infra.python.dto.ScoreBatchApiResponse.StockScoreApiResponse;
import com.quantlime.score.domain.Divergence;
import com.quantlime.score.domain.Quadrant;
import com.quantlime.score.domain.Score;
import com.quantlime.score.repository.ScoreRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ScorePersistenceServiceTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private ScoreRepository scoreRepository;

    @InjectMocks
    private ScorePersistenceService scorePersistenceService;

    @Test
    @DisplayName("[해당 날짜의 스코어가 없으면 새로 저장한다]")
    void saveAll_noExistingScoreToday_savesNewScore() {
        // given
        given(scoreRepository.findByStockCodeAndScoreDate(eq(STOCK_CODE), any()))
            .willReturn(Optional.empty());

        // when
        scorePersistenceService.saveAll(List.of(successResponse(STOCK_CODE, 82.0)));

        // then
        verify(scoreRepository).save(any(Score.class));
    }

    @Test
    @DisplayName("[퀀트 엔진이 내려준 사분면 코드가 그대로 Score에 저장된다]")
    void saveAll_withQuadrant_persistsQuadrant() {
        // given
        given(scoreRepository.findByStockCodeAndScoreDate(eq(STOCK_CODE), any()))
            .willReturn(Optional.empty());
        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);

        // when
        scorePersistenceService.saveAll(List.of(successResponse(STOCK_CODE, 82.0)));

        // then
        verify(scoreRepository).save(captor.capture());
        assertThat(captor.getValue().getQuadrant()).isEqualTo(Quadrant.TREND_UP_OVERSOLD);
    }

    @Test
    @DisplayName("[같은 날 재계산이면 기존 행을 갱신하고 새로 저장하지 않는다]")
    void saveAll_existingScoreToday_updatesInPlaceWithoutSave() {
        // given
        Score existing = Score.of(STOCK_CODE, LocalDate.now(), 50.0, 50.0, 50.0,
            null, null, Divergence.of(false, null), "이전 코멘트", false);
        given(scoreRepository.findByStockCodeAndScoreDate(eq(STOCK_CODE), any()))
            .willReturn(Optional.of(existing));

        // when
        scorePersistenceService.saveAll(List.of(successResponse(STOCK_CODE, 91.5)));

        // then
        assertThat(existing.getCompositeScore()).isEqualTo(91.5);
        verify(scoreRepository, never()).save(any(Score.class));
    }

    @Test
    @DisplayName("[배치 중 한 종목의 저장이 실패해도 나머지 종목은 계속 저장된다]")
    void saveAll_oneItemFails_othersStillPersisted() {
        // given: 두 번째 종목은 유니크 제약 경합(동시 재계산)으로 save가 실패한다고 가정
        String secondCode = "000660";
        String thirdCode = "035420";
        given(scoreRepository.findByStockCodeAndScoreDate(eq(STOCK_CODE), any()))
            .willReturn(Optional.empty());
        given(scoreRepository.findByStockCodeAndScoreDate(eq(secondCode), any()))
            .willReturn(Optional.empty());
        given(scoreRepository.findByStockCodeAndScoreDate(eq(thirdCode), any()))
            .willReturn(Optional.empty());
        given(scoreRepository.save(any(Score.class)))
            .willAnswer(invocation -> {
                Score score = invocation.getArgument(0);
                if (secondCode.equals(score.getStockCode())) {
                    throw new DataIntegrityViolationException("duplicate");
                }
                return score;
            });

        // when: 예외를 던지지 않고 정상적으로 반환되어야 한다
        scorePersistenceService.saveAll(List.of(
            successResponse(STOCK_CODE, 70.0),
            successResponse(secondCode, 60.0),
            successResponse(thirdCode, 50.0)));

        // then: 세 종목 모두 save가 시도되고(첫 번째·세 번째는 성공), 두 번째의 실패가
        // 세 번째 종목 저장을 막지 않는다
        verify(scoreRepository, times(3)).save(any(Score.class));
    }

    @Test
    @DisplayName("[퀀트 엔진이 알 수 없는 등급 코드를 반환해도 나머지 종목은 계속 저장된다]")
    void saveAll_unknownGradeCode_othersStillPersisted() {
        // given
        String invalidGradeCode = "000660";
        given(scoreRepository.findByStockCodeAndScoreDate(eq(STOCK_CODE), any()))
            .willReturn(Optional.empty());
        given(scoreRepository.findByStockCodeAndScoreDate(eq(invalidGradeCode), any()))
            .willReturn(Optional.empty());

        StockScoreApiResponse invalidGradeResponse = new StockScoreApiResponse(
            invalidGradeCode, 70.0, 50.0, 60.0, "UNKNOWN_GRADE", null,
            new DivergenceApiResponse(false, null), "코멘트", false);

        // when: Grade.of()가 ValidationException을 던져도 예외가 전파되지 않아야 한다
        scorePersistenceService.saveAll(List.of(
            successResponse(STOCK_CODE, 70.0), invalidGradeResponse));

        // then: 첫 번째 종목은 정상 저장된다
        verify(scoreRepository, times(1)).save(any(Score.class));
    }

    private StockScoreApiResponse successResponse(String stockCode, double compositeScore) {
        return new StockScoreApiResponse(
            stockCode, 70.0, 50.0, compositeScore, "BUY", "trend_up_oversold",
            new DivergenceApiResponse(false, null), "코멘트", false);
    }
}
