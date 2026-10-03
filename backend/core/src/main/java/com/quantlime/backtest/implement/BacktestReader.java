package com.quantlime.backtest.implement;

import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.backtest.domain.BacktestResult;
import com.quantlime.backtest.repository.BacktestDailyScoreRepository;
import com.quantlime.backtest.repository.BacktestResultRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 백테스트 결과·일별 스코어 조회를 감싸는 구현 레이어(Implementation) - 서비스가
 * Data Access(Repository)를 직접 건드리지 않고 이 컴포넌트를 통해서만 읽게 한다.
 * 저장은 {@link BacktestAppender}가 맡는다.
 */
@Component
@RequiredArgsConstructor
public class BacktestReader {

    private final BacktestResultRepository backtestResultRepository;
    private final BacktestDailyScoreRepository backtestDailyScoreRepository;

    /** 조회 API가 scoreVersion을 지정하지 않았을 때 "가장 최근에 계산된 결과" 한 건. */
    public Optional<BacktestResult> findLatestResult(String stockCode) {
        return backtestResultRepository.findTopByStockCodeOrderByBacktestDateDesc(stockCode);
    }

    /** 가장 최근에 쓰인 일별 스코어 한 건 - 자동 스케줄러가 최신 scoreVersion을 알아내는 용도. */
    public Optional<BacktestDailyScore> findLatestDailyScore() {
        return backtestDailyScoreRepository.findTopByOrderByIdDesc();
    }

    /** 한 버전의 (축, horizon)별 결과 - 축·horizon 오름차순. */
    public List<BacktestResult> findResults(String stockCode, String scoreVersion) {
        return backtestResultRepository
            .findByStockCodeAndScoreVersionOrderByAxisAscHorizonDaysAsc(stockCode, scoreVersion);
    }

    public List<BacktestDailyScore> findDailyScores(String stockCode, String scoreVersion) {
        return backtestDailyScoreRepository
            .findByStockCodeAndScoreVersionOrderByTradeDateAsc(stockCode, scoreVersion);
    }

    /** 횡단면 패널 구성용 - 여러 종목의 스코어를 한 번에(IN절) 읽어 종목·일자 오름차순으로 반환. */
    public List<BacktestDailyScore> findDailyScoresByStocks(List<String> stockCodes, String scoreVersion) {
        return backtestDailyScoreRepository
            .findByStockCodeInAndScoreVersionOrderByStockCodeAscTradeDateAsc(stockCodes, scoreVersion);
    }
}
