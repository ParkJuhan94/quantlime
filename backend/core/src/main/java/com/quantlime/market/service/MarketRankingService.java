package com.quantlime.market.service;

import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.dto.response.MarketRankingResponse;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketRankingService {

    private static final String LOSERS = "losers";

    private final MarketRankingCache marketRankingCache;

    public List<MarketRankingResponse> getRanking(String sort, int limit, Set<String> watchlistCodes) {
        return LOSERS.equals(sort)
            ? marketRankingCache.getLosers(limit, watchlistCodes)
            : marketRankingCache.getGainers(limit, watchlistCodes);
    }
}
