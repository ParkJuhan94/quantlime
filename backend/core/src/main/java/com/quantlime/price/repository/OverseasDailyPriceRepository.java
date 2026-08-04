package com.quantlime.price.repository;

import com.quantlime.price.domain.OverseasDailyPrice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverseasDailyPriceRepository
    extends JpaRepository<OverseasDailyPrice, Long>, OverseasDailyPriceQueryRepository {

    boolean existsByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    Optional<OverseasDailyPrice> findByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    long countByStockCode(String stockCode);

    Optional<OverseasDailyPrice> findTopByStockCodeOrderByTradeDateDesc(String stockCode);

    List<OverseasDailyPrice> findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(
        String stockCode, LocalDate startDate, LocalDate endDate);

    List<OverseasDailyPrice> findByStockCodeInAndTradeDateBetweenOrderByTradeDateDesc(
        List<String> stockCodes, LocalDate startDate, LocalDate endDate);
}
