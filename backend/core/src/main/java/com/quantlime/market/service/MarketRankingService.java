package com.quantlime.market.service;

import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.cache.TossMarketRankingCache;
import com.quantlime.market.domain.RankingPeriod;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.score.domain.Score;
import com.quantlime.score.implement.ScoreReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final ScoreReader scoreReader;

    public List<MarketRankingResponse> getRanking(String scope, String sort, int limit, Set<String> watchlistCodes) {
        return getRanking(scope, sort, limit, watchlistCodes, RankingPeriod.REALTIME);
    }

    public List<MarketRankingResponse> getRanking(
            String scope, String sort, int limit, Set<String> watchlistCodes, RankingPeriod period) {
        boolean isGainersOrLosers = SORT_GAINERS.equals(sort) || SORT_LOSERS.equals(sort);
        // 관심종목 자체계산 경로는 "오늘" 등락률만 계산하므로 실시간/1일일 때만
        // 쓴다 - 1주 이상 기간은 토스 랭킹(top100)을 관심종목으로 걸러서 보여준다.
        if (watchlistCodes != null && isGainersOrLosers && period.isIntraday()) {
            return enrichWithScore(watchlistRanking(scope, sort, limit, watchlistCodes));
        }

        List<MarketRankingResponse> ranked = tossMarketRankingCache.get(scope, sort, period);
        if (watchlistCodes != null) {
            ranked = ranked.stream().filter(item -> watchlistCodes.contains(item.stockCode())).toList();
        }
        return enrichWithScore(ranked.stream().limit(limit).toList());
    }

    private List<MarketRankingResponse> watchlistRanking(
            String scope, String sort, int limit, Set<String> watchlistCodes) {
        MarketRankingCache cache = SCOPE_OVERSEAS.equals(scope) ? overseasMarketRankingCache : domesticMarketRankingCache;
        return SORT_LOSERS.equals(sort)
            ? cache.getLosers(limit, watchlistCodes)
            : cache.getGainers(limit, watchlistCodes);
    }

    /**
     * 이미 상위 N개로 제한된 최종 결과에만 스코어를 조인한다 - 호출부가
     * (Toss 랭킹 캐시/관심종목 자체계산 캐시) 어느 경로로 만들어졌든 이
     * 한 곳에서만 스코어 DB를 조회한다. 스코어는 하루 2회만 갱신되는
     * 배치 데이터라 MarketRankingCache의 1초 로컬 TTL/100ms 스윕 안에서
     * 매번 재조회하면 낭비이므로 응답 직전에만 수행한다(MarketRankingResponse
     * 주석 참고). 구독 여부는 판별하지 않는다 - `/api/market/**`는 이미
     * 전체 공개 엔드포인트이고, 이 탭들의 스코어는 구독자 전용 기능
     * (스코어 탭, `/api/dashboard/scores`)과 별개로 모두에게 노출하기로
     * 결정했다(2026-09-24).
     */
    private List<MarketRankingResponse> enrichWithScore(List<MarketRankingResponse> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        List<String> codes = rows.stream().map(MarketRankingResponse::stockCode).toList();
        Map<String, Score> scoreByCode = scoreReader
            .findLatestScores(codes)
            .stream()
            .collect(Collectors.toMap(Score::getStockCode, Function.identity(), (a, b) -> a));

        return rows.stream().map(row -> withScore(row, scoreByCode.get(row.stockCode()))).toList();
    }

    private MarketRankingResponse withScore(MarketRankingResponse row, Score score) {
        if (score == null) {
            return row;
        }
        return new MarketRankingResponse(row.stockCode(), row.stockName(), row.sector(), row.currentPrice(),
            row.changeRate(), row.currency(), row.tradingVolume(), row.tradingAmount(), row.logoUrl(),
            row.detailAvailable(), score.getCompositeScore(), score.getCompositePercentile(),
            score.getGrade() != null ? score.getGrade().getLabel() : null);
    }
}
