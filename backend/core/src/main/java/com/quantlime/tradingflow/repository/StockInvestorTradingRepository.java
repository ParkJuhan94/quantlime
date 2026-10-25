package com.quantlime.tradingflow.repository;

import com.quantlime.tradingflow.domain.StockInvestorTrading;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockInvestorTradingRepository extends JpaRepository<StockInvestorTrading, Long> {

    List<StockInvestorTrading> findByStockCodeOrderByTradeDateDesc(String stockCode, Pageable pageable);

    List<StockInvestorTrading> findByStockCodeAndTradeDateBetween(String stockCode, LocalDate from, LocalDate to);

    long countByStockCode(String stockCode);

    @Query("select max(s.tradeDate) from StockInvestorTrading s")
    Optional<LocalDate> findLatestTradeDate();
}
