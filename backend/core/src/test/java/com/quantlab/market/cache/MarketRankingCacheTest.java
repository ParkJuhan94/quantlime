package com.quantlab.market.cache;

import com.quantlab.market.dto.response.MarketRankingResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class MarketRankingCacheTest {

    private final MarketRankingCache marketRankingCache = new MarketRankingCache();

    @Test
    @DisplayName("[상승률 내림차순으로 상위 N개를 반환한다]")
    void getGainers_returnsTopNByChangeRateDesc() {
        // given
        marketRankingCache.update(List.of(
            ranking("005930", 1.0),
            ranking("000660", 5.0),
            ranking("035420", -3.0)));

        // when
        List<MarketRankingResponse> result = marketRankingCache.getGainers(2);

        // then
        assertThat(result).extracting(MarketRankingResponse::stockCode)
            .containsExactly("000660", "005930");
    }

    @Test
    @DisplayName("[하락률 오름차순(가장 많이 떨어진 순)으로 상위 N개를 반환한다]")
    void getLosers_returnsTopNByChangeRateAsc() {
        // given
        marketRankingCache.update(List.of(
            ranking("005930", 1.0),
            ranking("000660", 5.0),
            ranking("035420", -3.0)));

        // when
        List<MarketRankingResponse> result = marketRankingCache.getLosers(2);

        // then
        assertThat(result).extracting(MarketRankingResponse::stockCode)
            .containsExactly("035420", "005930");
    }

    @Test
    @DisplayName("[limit이 전체 개수보다 크면 있는 만큼만 반환한다]")
    void getGainers_limitLargerThanSize_returnsAll() {
        // given
        marketRankingCache.update(List.of(ranking("005930", 1.0)));

        // when
        List<MarketRankingResponse> result = marketRankingCache.getGainers(10);

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("[갱신 전에는 빈 목록을 반환한다]")
    void getGainers_beforeAnyUpdate_returnsEmpty() {
        // when
        List<MarketRankingResponse> result = marketRankingCache.getGainers(10);

        // then
        assertThat(result).isEmpty();
    }

    private MarketRankingResponse ranking(String stockCode, double changeRate) {
        return new MarketRankingResponse(stockCode, stockCode + "-name", "전기전자", 10000L, changeRate);
    }
}
