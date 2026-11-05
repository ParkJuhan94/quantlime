package com.quantlime.price.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.toss.realtime.TossRealtimeMarket;
import com.quantlime.market.cache.TossMarketRankingCache;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.price.cache.DomesticMarketCalendarCache;
import com.quantlime.price.cache.OverseasMarketCalendarCache;
import com.quantlime.score.dto.response.ScoreRankingResponse;
import com.quantlime.score.service.ScoreService;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RankingRealtimeTargetResolverTest {

    @Mock private TossMarketRankingCache rankingCache;
    @Mock private ScoreService scoreService;
    @Mock private DomesticMarketCalendarCache domesticCalendar;
    @Mock private OverseasMarketCalendarCache overseasCalendar;
    @InjectMocks private RankingRealtimeTargetResolver resolver;

    private static List<MarketRankingResponse> ranking(String prefix, int size) {
        return IntStream.range(0, size).mapToObj(i -> {
            MarketRankingResponse row = mock(MarketRankingResponse.class);
            lenient().when(row.stockCode()).thenReturn(prefix + i);
            return row;
        }).toList();
    }

    private static ScoreRankingResponse score(String code, boolean overseas) {
        ScoreRankingResponse row = mock(ScoreRankingResponse.class);
        lenient().when(row.stockCode()).thenReturn(code);
        lenient().when(row.overseas()).thenReturn(overseas);
        return row;
    }

    @BeforeEach
    void setUp() {
        lenient().when(rankingCache.get(anyString(), anyString())).thenReturn(List.of());
        lenient().when(scoreService.getAllStocksScoreRanking(anyInt(), anyString())).thenReturn(List.of());
    }

    @Test
    @DisplayName("[양쪽 장이 닫혀 있으면 랭킹을 조회하지 않고 빈 대상을 준다]")
    void returnsEmptyWhenBothClosed() {
        // when
        Map<TossRealtimeMarket, List<String>> targets = resolver.resolve();

        // then
        assertThat(targets).isEmpty();
        verify(rankingCache, never()).get(anyString(), anyString());
    }

    @Test
    @DisplayName("[열린 시장의 탭별 상위 10행만 중복 없이 고른다]")
    void picksTopRowsOfOpenMarketWithoutDuplicates() {
        // given
        given(domesticCalendar.isMarketOpenNow()).willReturn(true);
        List<MarketRankingResponse> amount = ranking("A", 15);
        List<MarketRankingResponse> gainers = ranking("A", 3);
        given(rankingCache.get("domestic", TossMarketRankingCache.SORT_AMOUNT)).willReturn(amount);
        given(rankingCache.get("domestic", TossMarketRankingCache.SORT_GAINERS)).willReturn(gainers);

        // when
        Map<TossRealtimeMarket, List<String>> targets = resolver.resolve();

        // then
        assertThat(targets).containsOnlyKeys(TossRealtimeMarket.KR);
        assertThat(targets.get(TossRealtimeMarket.KR)).hasSize(10).startsWith("A0", "A1").doesNotContain("A10");
    }

    @Test
    @DisplayName("[스코어 전체 탭은 시장별로 나눠 넣는다]")
    void splitsAllScopeScoresByMarket() {
        // given
        given(domesticCalendar.isMarketOpenNow()).willReturn(true);
        given(overseasCalendar.isMarketOpenNow()).willReturn(true);
        List<ScoreRankingResponse> scores = List.of(score("005930", false), score("AAPL", true));
        given(scoreService.getAllStocksScoreRanking(10, "all")).willReturn(scores);

        // when
        Map<TossRealtimeMarket, List<String>> targets = resolver.resolve();

        // then
        assertThat(targets.get(TossRealtimeMarket.KR)).containsExactly("005930");
        assertThat(targets.get(TossRealtimeMarket.US)).containsExactly("AAPL");
    }

    @Test
    @DisplayName("[두 시장을 합쳐 연결당 한도 100개를 넘지 않는다]")
    void neverExceedsSubscriptionLimit() {
        // given
        given(domesticCalendar.isMarketOpenNow()).willReturn(true);
        given(overseasCalendar.isMarketOpenNow()).willReturn(true);
        for (String sort : List.of("amount", "gainers", "losers")) {
            List<MarketRankingResponse> kr = ranking("K" + sort, 10);
            List<MarketRankingResponse> us = ranking("U" + sort, 10);
            given(rankingCache.get("domestic", sort)).willReturn(kr);
            given(rankingCache.get("overseas", sort)).willReturn(us);
        }

        // when
        Map<TossRealtimeMarket, List<String>> targets = resolver.resolve();

        // then
        int total = targets.values().stream().mapToInt(List::size).sum();
        assertThat(total).isEqualTo(60).isLessThanOrEqualTo(RankingRealtimeTargetResolver.MAX_SUBSCRIPTIONS);
    }
}
