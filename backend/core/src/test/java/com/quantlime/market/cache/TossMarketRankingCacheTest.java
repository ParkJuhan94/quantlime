package com.quantlime.market.cache;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossRankingResponse;
import com.quantlime.infra.toss.dto.TossRankingResponse.RankedResult;
import com.quantlime.infra.toss.dto.TossRankingResponse.RankingItem;
import com.quantlime.infra.toss.dto.TossRankingResponse.RankingPrice;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TossMarketRankingCacheTest {

    @Mock
    private TossApiClient tossApiClient;

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private TossMarketRankingCache tossMarketRankingCache;

    @Test
    @DisplayName("[국내 급상승은 marketCountry=KR, type=TOP_GAINERS, duration=1d로 조회한다]")
    void get_domesticGainers_callsRankingApiWithKrTopGainers1d() {
        // given
        given(tossApiClient.getRankings("TOP_GAINERS", "KR", "1d", 100))
            .willReturn(emptyResponse());

        // when
        tossMarketRankingCache.get("domestic", "gainers");

        // then
        verify(tossApiClient, times(1)).getRankings("TOP_GAINERS", "KR", "1d", 100);
    }

    @Test
    @DisplayName("[해외 거래대금은 marketCountry=US, type=MARKET_TRADING_AMOUNT, duration=realtime로 조회한다]")
    void get_overseasAmount_callsRankingApiWithUsTradingAmountRealtime() {
        // given
        given(tossApiClient.getRankings("MARKET_TRADING_AMOUNT", "US", "realtime", 100))
            .willReturn(emptyResponse());

        // when
        tossMarketRankingCache.get("overseas", "amount");

        // then
        verify(tossApiClient, times(1)).getRankings("MARKET_TRADING_AMOUNT", "US", "realtime", 100);
    }

    @Test
    @DisplayName("[changeRate는 소수 비율을 퍼센트 숫자로 ×100 변환한다]")
    void get_convertsChangeRateFromFractionToPercentage() {
        // given: 0.0125 = 1.25%
        Stock stock = StockFixture.createStock("005930", "삼성전자");
        given(stockRepository.findByStockCodeIn(anyList())).willReturn(List.of(stock));
        given(tossApiClient.getRankings("TOP_GAINERS", "KR", "1d", 100)).willReturn(
            responseOf(new RankingItem(1, "005930", "KRW",
                new RankingPrice("56500", "55800", "0.0125"), "18432100", "1041436650000")));

        // when
        List<MarketRankingResponse> result = tossMarketRankingCache.get("domestic", "gainers");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).changeRate()).isCloseTo(1.25, offset(0.0001));
        assertThat(result.get(0).currentPrice()).isCloseTo(56500.0, offset(0.0001));
        assertThat(result.get(0).tradingAmount()).isCloseTo(1041436650000.0, offset(0.1));
        assertThat(result.get(0).logoUrl())
            .isEqualTo("https://ssl.pstatic.net/imgstock/fn/real/logo/png/stock/Stock005930.png");
        assertThat(result.get(0).detailAvailable()).isTrue();
    }

    @Test
    @DisplayName("[나스닥 종목은 로고 URL에 .O 접미사를 붙인다]")
    void get_nasdaqStock_logoUrlHasSuffix() {
        // given
        Stock stock = Stock.of("AAPL", "APPLE INC", MarketType.NASDAQ, ListingStatus.LISTED, "720", "애플");
        given(stockRepository.findByStockCodeIn(anyList())).willReturn(List.of(stock));
        given(tossApiClient.getRankings("TOP_GAINERS", "US", "1d", 100)).willReturn(
            responseOf(new RankingItem(1, "AAPL", "USD",
                new RankingPrice("220.0", "218.0", "0.0092"), "1000", "220000000")));

        // when
        List<MarketRankingResponse> result = tossMarketRankingCache.get("overseas", "gainers");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).stockName()).isEqualTo("애플");
        assertThat(result.get(0).logoUrl())
            .isEqualTo("https://ssl.pstatic.net/imgstock/fn/real/logo/png/stock/StockAAPL.O.png");
    }

    @Test
    @DisplayName("[로컬 stock 테이블에 없는 심볼은 이름 자리에 심볼 원문을 그대로 보여주고 로고 URL은 null이다]")
    void get_symbolNotInLocalStockTable_fallsBackToSymbolAsName() {
        // given: 해외 상위 종목이 백테스트 유니버스 밖인 경우를 재현 - stockRepository가 빈 목록 반환
        given(stockRepository.findByStockCodeIn(anyList())).willReturn(List.of());
        given(tossApiClient.getRankings("TOP_GAINERS", "US", "1d", 100)).willReturn(
            responseOf(new RankingItem(1, "AMIX", "USD",
                new RankingPrice("4.48", "2.75", "0.629"), "875663", "6029751898")));

        // when
        List<MarketRankingResponse> result = tossMarketRankingCache.get("overseas", "gainers");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).stockName()).isEqualTo("AMIX");
        assertThat(result.get(0).sector()).isNull();
        assertThat(result.get(0).logoUrl()).isNull();
        // 프론트가 이 값으로 상세페이지 진입/관심종목 등록을 막는다(둘 다
        // getStockByCode 호출을 거쳐 로컬 마스터에 없으면 404가 나므로).
        assertThat(result.get(0).detailAvailable()).isFalse();
    }

    @Test
    @DisplayName("[국내는 로컬 stock 테이블에 없는 심볼(ETF/ELW 등)을 랭킹에서 제외한다]")
    void get_domesticSymbolNotInLocalStockTable_isExcludedFromRanking() {
        // given: 삼성전자(로컬 존재)와 ETF 코드(로컬 미존재)가 함께 응답됨
        Stock stock = StockFixture.createStock("005930", "삼성전자");
        given(stockRepository.findByStockCodeIn(anyList())).willReturn(List.of(stock));
        given(tossApiClient.getRankings("MARKET_TRADING_AMOUNT", "KR", "realtime", 100)).willReturn(
            responseOf(
                new RankingItem(1, "005930", "KRW",
                    new RankingPrice("56500", "55800", "0.0125"), "18432100", "1041436650000"),
                new RankingItem(2, "069500", "KRW",
                    new RankingPrice("100535", "87643", "0.1472"), "1000", "243010465865")));

        // when
        List<MarketRankingResponse> result = tossMarketRankingCache.get("domestic", "amount");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).stockCode()).isEqualTo("005930");
        assertThat(result.get(0).stockName()).isEqualTo("삼성전자");
    }

    @Test
    @DisplayName("[랭킹이 집계되지 않은 조합(빈 배열)은 빈 목록을 반환한다]")
    void get_emptyRankings_returnsEmptyList() {
        // given
        given(tossApiClient.getRankings("TOP_GAINERS", "KR", "1d", 100)).willReturn(emptyResponse());

        // when
        List<MarketRankingResponse> result = tossMarketRankingCache.get("domestic", "gainers");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 Toss API를 다시 호출하지 않는다]")
    void get_withinTtl_doesNotRefetch() {
        // given
        given(tossApiClient.getRankings("TOP_GAINERS", "KR", "1d", 100)).willReturn(emptyResponse());

        // when
        tossMarketRankingCache.get("domestic", "gainers");
        tossMarketRankingCache.get("domestic", "gainers");

        // then
        verify(tossApiClient, times(1)).getRankings("TOP_GAINERS", "KR", "1d", 100);
    }

    @Test
    @DisplayName("[조회가 예외로 실패하면 예외를 전파하지 않고 빈 목록을 반환한다]")
    void get_apiThrows_returnsEmptyListWithoutThrowing() {
        // given
        given(tossApiClient.getRankings("TOP_GAINERS", "KR", "1d", 100))
            .willThrow(new RuntimeException("429 Too Many Requests"));

        // when
        List<MarketRankingResponse> result = tossMarketRankingCache.get("domestic", "gainers");

        // then
        assertThat(result).isEmpty();
    }

    private TossRankingResponse emptyResponse() {
        return new TossRankingResponse(new RankedResult(null, List.of()));
    }

    private TossRankingResponse responseOf(RankingItem... items) {
        return new TossRankingResponse(new RankedResult("2026-07-29T17:43:34+09:00", List.of(items)));
    }
}
