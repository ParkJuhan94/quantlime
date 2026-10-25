package com.quantlime.support;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.upbit.UpbitApiClient;
import com.quantlime.market.cache.BitcoinChartCache;
import com.quantlime.market.cache.DomesticIndexMinuteChartCache;
import com.quantlime.market.cache.ExchangeRateChartCache;
import com.quantlime.market.cache.MarketIndexCache;
import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.cache.OverseasIndexChartCache;
import com.quantlime.market.cache.TossMarketRankingCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.watchlist.service.WatchlistService;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * 시세·시장 캐시를 목으로 바꾸는 컨트롤러 테스트의 공용 부모.
 *
 * <p>{@code WatchlistService}를 목으로 바꾸기 때문에 실제 관심종목 로직이 필요한
 * {@code WatchlistControllerTest}는 {@link MockedServicesApiTestSupport}를 쓴다. 두 부모를 나눈 이유는
 * 그 충돌 하나뿐이고, 컨텍스트 수 이유와 사고 경위는 {@link MockedServicesApiTestSupport} 주석 참고.
 */
public abstract class MockedMarketApiTestSupport extends ApiTestSupport {

    @MockBean
    protected MarketIndexCache marketIndexCache;

    @MockBean(name = "domesticMarketRankingCache")
    protected MarketRankingCache domesticMarketRankingCache;

    @MockBean
    protected TossMarketRankingCache tossMarketRankingCache;

    @MockBean
    protected DomesticIndexMinuteChartCache domesticIndexMinuteChartCache;

    @MockBean
    protected OverseasIndexChartCache overseasIndexChartCache;

    @MockBean
    protected BitcoinChartCache bitcoinChartCache;

    @MockBean
    protected ExchangeRateChartCache exchangeRateChartCache;

    @MockBean
    protected UpbitApiClient upbitApiClient;

    @MockBean
    protected WatchlistService watchlistService;

    @MockBean
    protected TossApiClient tossApiClient;

    @MockBean
    protected PriceCacheStore priceCacheStore;
}
