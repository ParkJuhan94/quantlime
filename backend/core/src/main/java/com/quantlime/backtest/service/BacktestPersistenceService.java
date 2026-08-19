package com.quantlime.backtest.service;

import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.backtest.domain.BacktestResult;
import com.quantlime.backtest.domain.CrossSectionalBacktestResult;
import com.quantlime.backtest.repository.BacktestDailyScoreRepository;
import com.quantlime.backtest.repository.BacktestResultRepository;
import com.quantlime.backtest.repository.CrossSectionalBacktestResultRepository;
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
    private final BacktestDailyScoreRepository backtestDailyScoreRepository;
    private final CrossSectionalBacktestResultRepository crossSectionalBacktestResultRepository;

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

    /**
     * 일별 스코어는 BacktestResult처럼 고정된 소수 행이 아니라 종목당
     * 300~400건에 달하고, 재실행 시 "이전 계산분을 전부 교체"하는 의미가
     * 더 명확하다 - 행별 find-or-update 대신 통째로 지우고 다시 넣는다.
     */
    @Transactional
    public void replaceDailyScores(String stockCode, String scoreVersion, List<BacktestDailyScore> dailyScores) {
        backtestDailyScoreRepository.deleteByStockCodeAndScoreVersion(stockCode, scoreVersion);
        backtestDailyScoreRepository.saveAll(dailyScores);
    }

    /**
     * 횡단면 백테스트 결과 하나(시장·축·horizon·버전·표본분할 조합)를
     * upsert한다 - saveOne(BacktestResult)과 동일한 find-or-update 패턴.
     * {@link CrossSectionalBacktestService}가 조합마다 개별 호출하므로 리스트
     * 배치가 아니라 단건 메서드로 둔다.
     */
    @Transactional
    public void saveCrossSectional(CrossSectionalBacktestResult result) {
        crossSectionalBacktestResultRepository.findByMarketTypeAndAxisAndHorizonDaysAndScoreVersionAndSampleSplit(
                result.getMarketType(), result.getAxis(), result.getHorizonDays(),
                result.getScoreVersion(), result.getSampleSplit())
            .ifPresentOrElse(
                existing -> existing.updateFrom(
                    result.getBacktestDate(), result.getStockCount(), result.getMeanIc(),
                    result.getIcCiLow(), result.getIcCiHigh(), result.getSampleDates(),
                    result.getSampleObservations(), result.getNullMean(), result.getNullStd(),
                    result.getNullPercentileLow(), result.getNullPercentileHigh(), result.getBuckets()),
                () -> crossSectionalBacktestResultRepository.save(result));
    }
}
