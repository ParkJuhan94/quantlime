package com.quantlime.market.service;

import com.quantlime.market.cache.DomesticMarketRankingCache;
import com.quantlime.market.cache.TossMarketRankingCache;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.util.ChangeRateCalculator;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.dto.mapper.StockMapper;
import com.quantlime.stock.repository.StockRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 랭킹 조회는 두 경로로 나뉜다.
 * <ul>
 *   <li>비관심종목(top-N 공개 랭킹) 또는 거래대금/거래량 정렬 -
 *       {@link TossMarketRankingCache}(Toss `/api/v1/rankings`, 국내+해외
 *       공용, 최대 100건)
 *   <li>관심종목만 보기 + 등락률 정렬(gainers/losers) - 자체 계산 경로.
 *       Toss 랭킹 API가 top100까지만 줘서 관심종목이 그 밖에 있으면
 *       걸러질 수 있기 때문에(2026-07-29 결정), 국내는 기존
 *       {@link DomesticMarketRankingCache}(전종목 자체 계산), 해외는
 *       {@link PriceCacheStore}+{@link PreviousCloseCache}로
 *       직접 계산해 관심종목 전체를 대상으로 정확히 필터링한다.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MarketRankingService {

    private static final String SORT_GAINERS = TossMarketRankingCache.SORT_GAINERS;
    private static final String SORT_LOSERS = TossMarketRankingCache.SORT_LOSERS;
    private static final String SCOPE_OVERSEAS = TossMarketRankingCache.SCOPE_OVERSEAS;
    private static final String CURRENCY_USD = "USD";

    private final DomesticMarketRankingCache domesticMarketRankingCache;
    private final TossMarketRankingCache tossMarketRankingCache;
    private final StockRepository stockRepository;
    private final PriceCacheStore priceCacheStore;
    private final PreviousCloseCache overseasPreviousCloseCache;

    public List<MarketRankingResponse> getRanking(String scope, String sort, int limit, Set<String> watchlistCodes) {
        boolean isGainersOrLosers = SORT_GAINERS.equals(sort) || SORT_LOSERS.equals(sort);
        if (watchlistCodes != null && isGainersOrLosers) {
            return SCOPE_OVERSEAS.equals(scope)
                ? overseasWatchlistRanking(sort, limit, watchlistCodes)
                : domesticWatchlistRanking(sort, limit, watchlistCodes);
        }

        List<MarketRankingResponse> ranked = tossMarketRankingCache.get(scope, sort);
        if (watchlistCodes != null) {
            ranked = ranked.stream().filter(item -> watchlistCodes.contains(item.stockCode())).toList();
        }
        return ranked.stream().limit(limit).toList();
    }

    private List<MarketRankingResponse> domesticWatchlistRanking(String sort, int limit, Set<String> watchlistCodes) {
        return SORT_LOSERS.equals(sort)
            ? domesticMarketRankingCache.getLosers(limit, watchlistCodes)
            : domesticMarketRankingCache.getGainers(limit, watchlistCodes);
    }

    /**
     * 해외 관심종목 자체 계산 랭킹. {@link PriceCacheStore}는
     * {@code OverseasWatchlistPriceScheduler}가 이미 채워두고, 전일종가는
     * {@link PreviousCloseCache}로 얻어 국내와 동일한 방식(전일
     * 종가 대비 등락률)으로 계산한다. 시세 캐시 미스(스케줄러가 아직
     * 한 틱도 안 돌았거나 미국장이 닫혀 있는 경우)인 종목은 결과에서
     * 조용히 제외한다.
     */
    private List<MarketRankingResponse> overseasWatchlistRanking(String sort, int limit, Set<String> watchlistCodes) {
        List<Stock> overseasStocks = stockRepository.findByStockCodeIn(List.copyOf(watchlistCodes)).stream()
            .filter(stock -> !stock.getMarketType().isDomestic())
            .toList();
        if (overseasStocks.isEmpty()) {
            return List.of();
        }

        List<String> codes = overseasStocks.stream().map(Stock::getStockCode).toList();
        Map<String, Double> previousCloseByCode = overseasPreviousCloseCache.get(codes);

        List<MarketRankingResponse> items = new ArrayList<>();
        for (Stock stock : overseasStocks) {
            priceCacheStore.find(stock.getStockCode()).ifPresent(snapshot -> {
                Double previousClose = previousCloseByCode.get(stock.getStockCode());
                Double changeRate = ChangeRateCalculator.calculate(snapshot.currentPrice(), previousClose);
                if (changeRate != null) {
                    items.add(new MarketRankingResponse(stock.getStockCode(), stock.getDisplayName(), stock.getSector(),
                        snapshot.currentPrice(), changeRate, CURRENCY_USD, null, null, StockMapper.toLogoUrl(stock),
                        true));
                }
            });
        }

        Comparator<MarketRankingResponse> comparator = Comparator.comparingDouble(MarketRankingResponse::changeRate);
        if (SORT_GAINERS.equals(sort)) {
            comparator = comparator.reversed();
        }
        return items.stream().sorted(comparator).limit(limit).toList();
    }
}
