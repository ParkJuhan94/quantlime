package com.quantlime.backtest.service;

import com.quantlime.backtest.domain.BacktestResult;
import com.quantlime.backtest.repository.BacktestResultRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 퀀트 엔진 백테스트 응답을 {@link BacktestResult}로 영속화하는 책임만 분리.
 * {@link BacktestService}가 외부 HTTP 호출까지 감싸는 트랜잭션을 만들지
 * 않도록 저장 전용 빈으로 둔다(ScorePersistenceService와 동일한 이유).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestPersistenceService {

    private final BacktestResultRepository backtestResultRepository;

    /**
     * (축, horizon) 행 하나의 저장 실패가 나머지 행 저장까지 막지 않도록
     * 항목별로 예외를 격리한다(ScorePersistenceService.saveAll과 동일 패턴).
     */
    @Transactional
    public void saveAll(List<BacktestResult> results) {
        for (BacktestResult result : results) {
            try {
                saveOne(result);
            } catch (Exception e) {
                log.error("백테스트 결과 저장 실패(해당 행만 스킵): stockCode={}, axis={}, "
                        + "horizonDays={}, error={}",
                    result.getStockCode(), result.getAxis(), result.getHorizonDays(),
                    e.getMessage(), e);
            }
        }
    }

    private void saveOne(BacktestResult result) {
        backtestResultRepository.findByStockCodeAndAxisAndHorizonDaysAndScoreVersion(
                result.getStockCode(), result.getAxis(), result.getHorizonDays(), result.getScoreVersion())
            .ifPresentOrElse(
                existing -> existing.updateFrom(
                    result.getBacktestDate(), result.getSampleSize(), result.getRankIc(),
                    result.getRankIcCiLow(), result.getRankIcCiHigh(),
                    result.getScoreAutocorrelation(), result.getGradeFlipRate(), result.getBuckets()),
                () -> backtestResultRepository.save(result));
    }
}
