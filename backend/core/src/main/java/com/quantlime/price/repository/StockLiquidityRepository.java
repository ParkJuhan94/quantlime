package com.quantlime.price.repository;

import com.quantlime.price.domain.StockLiquidity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockLiquidityRepository extends JpaRepository<StockLiquidity, Long> {

    Optional<StockLiquidity> findByStockCode(String stockCode);

    List<StockLiquidity> findAllByStockCodeIn(List<String> stockCodes);
}
