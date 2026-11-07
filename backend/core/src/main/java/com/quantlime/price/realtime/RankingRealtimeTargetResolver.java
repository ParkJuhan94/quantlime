package com.quantlime.price.realtime;

import com.quantlime.infra.toss.realtime.TossRealtimeMarket;
import com.quantlime.market.cache.TossMarketRankingCache;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.price.cache.DomesticMarketCalendarCache;
import com.quantlime.price.cache.OverseasMarketCalendarCache;
import com.quantlime.score.dto.response.ScoreRankingResponse;
import com.quantlime.score.service.ScoreService;
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
 * 탭은 거래대금·급상승·급하락·스코어라 시장당 최대 40개이고 연결당 한도 {@value #MAX_SUBSCRIPTIONS}개 안에 든다.
 *
 * <p>한 번에 한 시장만 구독한다. 국내장(NXT 포함)이 열려 있으면 국내만, 닫혀 있으면 미국 프리·정규·애프터마켓을
 * 구독한다. 미국 시간대가 겹치는 국내 08:00 이후에는 국내가 우선이다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "toss-realtime.enabled", havingValue = "true")
public class RankingRealtimeTargetResolver {

    static final int TOP_ROWS_PER_TAB = 10;
    static final int MAX_SUBSCRIPTIONS = 100;

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
        if (domesticMarketCalendarCache.isMarketOpenNow()) {
            targets.put(TossRealtimeMarket.KR, limit(topCodes(TossMarketRankingCache.SCOPE_DOMESTIC)));
        } else if (overseasMarketCalendarCache.isExtendedSessionOpenNow()) {
            targets.put(TossRealtimeMarket.US, limit(topCodes(TossMarketRankingCache.SCOPE_OVERSEAS)));
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

    private static List<String> limit(Set<String> codes) {
        return codes.stream().limit(MAX_SUBSCRIPTIONS).toList();
    }
}
