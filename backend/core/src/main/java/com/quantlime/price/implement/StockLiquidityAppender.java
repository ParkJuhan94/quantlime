package com.quantlime.price.implement;

import com.quantlime.price.domain.StockLiquidity;
import com.quantlime.price.repository.StockLiquidityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 종목 유동성 스냅샷 저장을 감싸는 구현 레이어(Implementation). */
@Component
@RequiredArgsConstructor
public class StockLiquidityAppender {

    private final StockLiquidityRepository stockLiquidityRepository;

    public void save(StockLiquidity liquidity) {
        stockLiquidityRepository.save(liquidity);
    }
}
