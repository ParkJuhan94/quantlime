package com.quantlime.price.repository;

import com.quantlime.price.domain.DomesticRegularClosePrice;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomesticRegularClosePriceRepository
    extends JpaRepository<DomesticRegularClosePrice, Long>, DomesticRegularClosePriceQueryRepository {

    Optional<DomesticRegularClosePrice> findByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    boolean existsByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);
}
