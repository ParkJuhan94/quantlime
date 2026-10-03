package com.quantlime.price.implement;

import com.quantlime.price.domain.StockLiquidity;
import com.quantlime.price.repository.StockLiquidityRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 종목 유동성 스냅샷 조회를 감싸는 구현 레이어(Implementation). */
@Component
@RequiredArgsConstructor
public class StockLiquidityReader {

    private final StockLiquidityRepository stockLiquidityRepository;

    public Optional<StockLiquidity> findByStockCode(String stockCode) {
        return stockLiquidityRepository.findByStockCode(stockCode);
    }

    public List<StockLiquidity> findAllByStockCodes(List<String> stockCodes) {
        return stockLiquidityRepository.findAllByStockCodeIn(stockCodes);
    }
}
