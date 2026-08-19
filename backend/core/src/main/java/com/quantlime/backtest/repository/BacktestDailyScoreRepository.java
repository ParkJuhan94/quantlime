package com.quantlime.backtest.repository;

import com.quantlime.backtest.domain.BacktestDailyScore;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BacktestDailyScoreRepository extends JpaRepository<BacktestDailyScore, Long> {

    List<BacktestDailyScore> findByStockCodeAndScoreVersionOrderByTradeDateAsc(
        String stockCode, String scoreVersion);

    // CrossSectionalBacktestService가 시장 하나(최대 500종목)의 저장된
    // 스코어를 한 번에 읽어 횡단면 패널을 구성할 때 쓴다 - 종목별로 개별
    // 조회하면 최대 500번 왕복이 되므로 IN절로 한 번에 가져온다.
    List<BacktestDailyScore> findByStockCodeInAndScoreVersionOrderByStockCodeAscTradeDateAsc(
        List<String> stockCodes, String scoreVersion);

    // 파생 delete 메서드(deleteBy...)는 대상 엔티티를 로드해 각각
    // entityManager.remove()를 호출하는데, 이 삭제는 액션 큐에 쌓일 뿐 즉시
    // 실행되지 않는다. Hibernate는 같은 플러시 안에서 삭제보다 삽입을 먼저
    // 실행하므로(insert -> ... -> delete 순서), 뒤이은 saveAll의 INSERT가
    // "아직 DB에는 남아있는" 이전 행과 유니크 제약이 충돌해 재실행 시마다
    // ConstraintViolationException이 발생했다(실제 라이브 재실행으로 재현).
    // @Modifying 벌크 쿼리는 액션 큐를 거치지 않고 즉시 실행되는 단일 DELETE
    // SQL이라 이 순서 문제 자체가 생기지 않는다.
    @Modifying
    @Query("DELETE FROM BacktestDailyScore d WHERE d.stockCode = :stockCode AND d.scoreVersion = :scoreVersion")
    void deleteByStockCodeAndScoreVersion(
        @Param("stockCode") String stockCode, @Param("scoreVersion") String scoreVersion);
}
