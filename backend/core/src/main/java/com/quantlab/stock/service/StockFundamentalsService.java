package com.quantlab.stock.service;

import com.quantlab.stock.cache.StockFundamentalsCache;
import com.quantlab.stock.dto.response.StockFundamentalsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockFundamentalsService {

    private final StockFundamentalsCache stockFundamentalsCache;

    public StockFundamentalsResponse getFundamentals(String stockCode) {
        return stockFundamentalsCache.get(stockCode);
    }
}
