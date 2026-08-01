package com.quantlime.price.repository;

import com.quantlime.price.domain.DomesticDailyPrice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomesticDailyPriceRepository
    extends JpaRepository<DomesticDailyPrice, Long>, DomesticDailyPriceQueryRepository {

    Optional<DomesticDailyPrice> findByStockCodeAndTradeDate(
        String stockCode, LocalDate tradeDate);

    List<DomesticDailyPrice> findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(
        String stockCode, LocalDate startDate, LocalDate endDate);

    List<DomesticDailyPrice> findByStockCodeInAndTradeDateBetweenOrderByTradeDateDesc(
        List<String> stockCodes, LocalDate startDate, LocalDate endDate);

    boolean existsByStockCodeAndTradeDate(
        String stockCode, LocalDate tradeDate);

    long countByStockCode(String stockCode);

    Optional<DomesticDailyPrice> findTopByStockCodeOrderByTradeDateDesc(String stockCode);
}
