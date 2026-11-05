package com.quantlime.price.realtime;

import com.quantlime.infra.toss.realtime.TossRealtimeMarket;
import com.quantlime.market.cache.TossMarketRankingCache;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.price.cache.DomesticMarketCalendarCache;
import com.quantlime.price.cache.OverseasMarketCalendarCache;
import com.quantlime.score.dto.response.ScoreRankingResponse;
import com.quantlime.score.service.ScoreService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 웹소켓으로 구독할 종목을 고른다 - 랭킹 표 각 탭의 상위 {@value #TOP_ROWS_PER_TAB}행이다.
 * 현재 탭은 국내·해외 × 거래대금·급상승·급하락·스코어라 최대 90개(스코어 "전체" 포함)이고, 연결당 한도
 * {@value #MAX_SUBSCRIPTIONS}개 안에 든다. 장이 닫힌 시장은 받을 체결이 없어 빼므로 실제로는
 * 더 적다. 앞쪽 탭부터 채우므로 한도를 넘어도 잘리는 건 뒤쪽 탭의 행이다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "toss-realtime.enabled", havingValue = "true")
public class RankingRealtimeTargetResolver {

    static final int TOP_ROWS_PER_TAB = 10;
    static final int MAX_SUBSCRIPTIONS = 100;

    private static final String SCOPE_ALL = "all";
    private static final List<String> SORTS = List.of(
        TossMarketRankingCache.SORT_AMOUNT,
        TossMarketRankingCache.SORT_GAINERS,
        TossMarketRankingCache.SORT_LOSERS);

    private final TossMarketRankingCache tossMarketRankingCache;
    private final ScoreService scoreService;
    private final DomesticMarketCalendarCache domesticMarketCalendarCache;
    private final OverseasMarketCalendarCache overseasMarketCalendarCache;

    public Map<TossRealtimeMarket, List<String>> resolve() {
        Map<TossRealtimeMarket, List<String>> targets = new EnumMap<>(TossRealtimeMarket.class);
        boolean domesticOpen = domesticMarketCalendarCache.isMarketOpenNow();
        boolean overseasOpen = overseasMarketCalendarCache.isMarketOpenNow();
        if (!domesticOpen && !overseasOpen) {
            return targets;
        }
        // 스코어의 "전체" 탭은 양쪽 시장이 섞여 있어 시장별로 나눠 넣는다.
        List<ScoreRankingResponse> allScores = scoreService.getAllStocksScoreRanking(TOP_ROWS_PER_TAB, SCOPE_ALL);
        int remaining = MAX_SUBSCRIPTIONS;
        if (domesticOpen) {
            Set<String> codes = topCodes(TossMarketRankingCache.SCOPE_DOMESTIC);
            allScores.stream().filter(score -> !score.overseas()).map(ScoreRankingResponse::stockCode).forEach(codes::add);
            List<String> limited = limit(codes, remaining);
            targets.put(TossRealtimeMarket.KR, limited);
            remaining -= limited.size();
        }
        if (overseasOpen) {
            Set<String> codes = topCodes(TossMarketRankingCache.SCOPE_OVERSEAS);
            allScores.stream().filter(ScoreRankingResponse::overseas).map(ScoreRankingResponse::stockCode).forEach(codes::add);
            targets.put(TossRealtimeMarket.US, limit(codes, remaining));
        }
        return targets;
    }

    private Set<String> topCodes(String scope) {
        Set<String> codes = new LinkedHashSet<>();
        for (String sort : SORTS) {
            tossMarketRankingCache.get(scope, sort).stream()
                .limit(TOP_ROWS_PER_TAB)
                .map(MarketRankingResponse::stockCode)
                .forEach(codes::add);
        }
        scoreService.getAllStocksScoreRanking(TOP_ROWS_PER_TAB, scope).stream()
            .map(ScoreRankingResponse::stockCode)
            .forEach(codes::add);
        return codes;
    }

    private static List<String> limit(Set<String> codes, int max) {
        List<String> ordered = new ArrayList<>(codes);
        return ordered.size() > max ? List.copyOf(ordered.subList(0, max)) : List.copyOf(ordered);
    }
}
