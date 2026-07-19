package com.quantlab.market.repository;

import com.quantlab.market.domain.BenchmarkIndex;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenchmarkIndexRepository extends JpaRepository<BenchmarkIndex, Long> {

    boolean existsByIndexCodeAndTradeDate(String indexCode, LocalDate tradeDate);

    long countByIndexCode(String indexCode);

    List<BenchmarkIndex> findByIndexCodeAndTradeDateBetweenOrderByTradeDateAsc(
        String indexCode, LocalDate startDate, LocalDate endDate);
}
