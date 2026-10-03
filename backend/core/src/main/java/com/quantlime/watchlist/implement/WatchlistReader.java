package com.quantlime.watchlist.implement;

import com.quantlime.stock.domain.MarketType;
import com.quantlime.watchlist.repository.WatchlistRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 관심 종목 조회를 감싸는 구현 레이어(Implementation). */
@Component
@RequiredArgsConstructor
public class WatchlistReader {

    private final WatchlistRepository watchlistRepository;

    /** 시장 구분별로 누군가 관심 등록한 종목코드(중복 제거). */
    public List<String> findDistinctStockCodesByMarketTypeIn(List<MarketType> marketTypes) {
        return watchlistRepository.findDistinctStockCodesByMarketTypeIn(marketTypes);
    }
}
