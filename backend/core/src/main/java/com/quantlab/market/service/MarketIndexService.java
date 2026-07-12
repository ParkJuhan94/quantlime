package com.quantlab.market.service;

import com.quantlab.market.cache.MarketIndexCache;
import com.quantlab.market.dto.response.MarketIndexResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketIndexService {

    private final MarketIndexCache marketIndexCache;

    public MarketIndexResponse getIndices() {
        return marketIndexCache.get();
    }
}
