package com.quantlime.backtest.repository;

import com.quantlime.backtest.domain.BacktestAxis;
import com.quantlime.backtest.domain.BacktestSampleSplit;
import com.quantlime.backtest.domain.CrossSectionalBacktestResult;
import com.quantlime.stock.domain.MarketType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrossSectionalBacktestResultRepository extends JpaRepository<CrossSectionalBacktestResult, Long> {

    Optional<CrossSectionalBacktestResult> findByMarketTypeAndAxisAndHorizonDaysAndScoreVersionAndSampleSplit(
        MarketType marketType, BacktestAxis axis, int horizonDays, String scoreVersion,
        BacktestSampleSplit sampleSplit);

    // BacktestResultRepository와 동일한 이유 - buckets가 @ElementCollection(LAZY)이라
    // join fetch 없이 순회하면 행마다 별도 SELECT가 나가는 N+1이 된다.
    @Query("SELECT r FROM CrossSectionalBacktestResult r LEFT JOIN FETCH r.buckets "
        + "WHERE r.scoreVersion = :scoreVersion "
        + "ORDER BY r.marketType ASC, r.axis ASC, r.horizonDays ASC")
    List<CrossSectionalBacktestResult> findByScoreVersionOrderByMarketTypeAscAxisAscHorizonDaysAsc(
        @Param("scoreVersion") String scoreVersion);
}
