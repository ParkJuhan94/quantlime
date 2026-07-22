package com.quantlime.stock.service;

import com.quantlime.stock.cache.StockFundamentalsCache;
import com.quantlime.stock.dto.response.StockFundamentalsResponse;
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
