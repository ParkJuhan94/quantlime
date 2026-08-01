package com.quantlime.market.repository;

import com.quantlime.market.domain.BenchmarkIndex;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenchmarkIndexRepository extends JpaRepository<BenchmarkIndex, Long> {

    boolean existsByIndexCodeAndTradeDate(String indexCode, LocalDate tradeDate);

    long countByIndexCode(String indexCode);

    List<BenchmarkIndex> findByIndexCodeAndTradeDateBetweenOrderByTradeDateAsc(
        String indexCode, LocalDate startDate, LocalDate endDate);

    /**
     * 지정일 이전(미포함) 최신 종가 - 지수 현재가의 등락률을 자체 계산할 때
     * "전일 종가" 기준으로 쓴다(DomesticDailyPriceQueryRepository.findLatestBeforeDate와
     * 동일한 성격, MarketIndexCache 참고).
     */
    Optional<BenchmarkIndex> findTopByIndexCodeAndTradeDateLessThanOrderByTradeDateDesc(
        String indexCode, LocalDate date);
}
