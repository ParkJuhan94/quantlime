package com.quantlime.tradingflow.repository;

import com.quantlime.tradingflow.domain.StockInvestorTrading;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockInvestorTradingRepository extends JpaRepository<StockInvestorTrading, Long> {

    List<StockInvestorTrading> findByStockCodeOrderByTradeDateDesc(String stockCode, Pageable pageable);

    List<StockInvestorTrading> findByStockCodeAndTradeDateBetween(String stockCode, LocalDate from, LocalDate to);

    long countByStockCode(String stockCode);

    // 확정된 행(개인이 채워진 행)인데 기관 세부가 비어 있는 수 - 세부 저장 이전에 쌓인 이력의 재백필 필요 여부를 가린다.
    @Query("""
        select count(s) from StockInvestorTrading s
        where s.stockCode = :stockCode
          and s.individual.netBuyVolume is not null
          and s.institutionBreakdown.pensionFundNetBuyVolume is null
        """)
    long countConfirmedWithoutBreakdown(@Param("stockCode") String stockCode);

    @Query("select max(s.tradeDate) from StockInvestorTrading s")
    Optional<LocalDate> findLatestTradeDate();
}
