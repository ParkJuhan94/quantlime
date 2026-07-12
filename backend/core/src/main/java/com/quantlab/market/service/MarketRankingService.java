package com.quantlab.market.service;

import com.quantlab.market.cache.MarketRankingCache;
import com.quantlab.market.dto.response.MarketRankingResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketRankingService {

    private static final String LOSERS = "losers";

    private final MarketRankingCache marketRankingCache;

    public List<MarketRankingResponse> getRanking(String sort, int limit) {
        return LOSERS.equals(sort)
            ? marketRankingCache.getLosers(limit)
            : marketRankingCache.getGainers(limit);
    }
}
