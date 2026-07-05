package com.quantlab.price.repository;

import com.quantlab.price.domain.DailyPrice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {

    Optional<DailyPrice> findByStockCodeAndTradeDate(
        String stockCode, LocalDate tradeDate);

    List<DailyPrice> findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(
        String stockCode, LocalDate startDate, LocalDate endDate);

    boolean existsByStockCodeAndTradeDate(
        String stockCode, LocalDate tradeDate);

    long countByStockCode(String stockCode);
}
