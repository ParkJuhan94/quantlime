package com.quantlime.backtest.repository;

import com.quantlime.backtest.domain.BacktestAxis;
import com.quantlime.backtest.domain.BacktestResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BacktestResultRepository extends JpaRepository<BacktestResult, Long> {

    Optional<BacktestResult> findByStockCodeAndAxisAndHorizonDaysAndScoreVersion(
        String stockCode, BacktestAxis axis, int horizonDays, String scoreVersion);

    // 조회 응답 하나당 (축, horizon) 최대 8행이라 규모는 작지만, buckets가
    // @ElementCollection(LAZY)이라 join fetch 없이 순회하면 행마다 별도
    // SELECT가 나가는 N+1이 된다(9.8 컨벤션 "N+1 방지: fetchJoin() 활용").
    @Query("SELECT br FROM BacktestResult br LEFT JOIN FETCH br.buckets "
        + "WHERE br.stockCode = :stockCode AND br.scoreVersion = :scoreVersion "
        + "ORDER BY br.axis ASC, br.horizonDays ASC")
    List<BacktestResult> findByStockCodeAndScoreVersionOrderByAxisAscHorizonDaysAsc(
        @Param("stockCode") String stockCode, @Param("scoreVersion") String scoreVersion);

    // 조회 API가 scoreVersion을 지정하지 않았을 때 "가장 최근에 계산된 버전"을
    // 기본값으로 찾기 위함(백테스트 결과가 여러 버전에 걸쳐 쌓일 수 있으므로).
    Optional<BacktestResult> findTopByStockCodeOrderByBacktestDateDesc(String stockCode);
}
