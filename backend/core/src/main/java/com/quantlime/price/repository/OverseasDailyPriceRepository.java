package com.quantlime.price.repository;

import com.quantlime.price.domain.OverseasDailyPrice;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverseasDailyPriceRepository
    extends JpaRepository<OverseasDailyPrice, Long>, OverseasDailyPriceQueryRepository {

    boolean existsByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    long countByStockCode(String stockCode);
}
