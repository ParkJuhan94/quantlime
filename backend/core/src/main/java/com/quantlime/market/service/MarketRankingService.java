package com.quantlime.market.service;

import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.cache.TossMarketRankingCache;
import com.quantlime.market.dto.response.MarketRankingResponse;
import java.util.List;
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
 *       걸러질 수 있기 때문에(2026-07-29 결정), 국내/해외 둘 다
 *       {@link MarketRankingCache}(리더 인스턴스가 매 틱 계산해 Redis에
 *       쓴 스냅샷을 읽는 캐시)로 관심종목 전체를 대상으로 정확히
 *       필터링한다(2026-09-25 - 이전엔 국내만 전용 캐시가 있었고 해외는
 *       이 서비스가 매 HTTP 요청마다 PriceCacheStore+PreviousCloseCache를
 *       직접 계산했는데, {@code PriceRelayLeaderGate} 도입에 맞춰 국내와
 *       동일한 "리더가 계산 → Redis에 씀" 패턴으로 통일했다 - 계산 코드
 *       자체는 {@code OverseasWatchlistPriceScheduler}로 옮겨감).
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MarketRankingService {

    private static final String SORT_GAINERS = TossMarketRankingCache.SORT_GAINERS;
    private static final String SORT_LOSERS = TossMarketRankingCache.SORT_LOSERS;
    private static final String SCOPE_OVERSEAS = TossMarketRankingCache.SCOPE_OVERSEAS;

    // 필드명이 MarketRankingCacheConfig의 @Bean 메서드명과 일치해야
    // Spring이 같은 타입(MarketRankingCache)의 두 Bean 중 이걸 고른다
    // (By-Name 디스앰비규에이션 - WatchlistedStockCodeCache 등과 동일 관례).
    private final MarketRankingCache domesticMarketRankingCache;
    private final MarketRankingCache overseasMarketRankingCache;
    private final TossMarketRankingCache tossMarketRankingCache;

    public List<MarketRankingResponse> getRanking(String scope, String sort, int limit, Set<String> watchlistCodes) {
        boolean isGainersOrLosers = SORT_GAINERS.equals(sort) || SORT_LOSERS.equals(sort);
        if (watchlistCodes != null && isGainersOrLosers) {
            return watchlistRanking(scope, sort, limit, watchlistCodes);
        }

        List<MarketRankingResponse> ranked = tossMarketRankingCache.get(scope, sort);
        if (watchlistCodes != null) {
            ranked = ranked.stream().filter(item -> watchlistCodes.contains(item.stockCode())).toList();
        }
        return ranked.stream().limit(limit).toList();
    }

    private List<MarketRankingResponse> watchlistRanking(
            String scope, String sort, int limit, Set<String> watchlistCodes) {
        MarketRankingCache cache = SCOPE_OVERSEAS.equals(scope) ? overseasMarketRankingCache : domesticMarketRankingCache;
        return SORT_LOSERS.equals(sort)
            ? cache.getLosers(limit, watchlistCodes)
            : cache.getGainers(limit, watchlistCodes);
    }
}
