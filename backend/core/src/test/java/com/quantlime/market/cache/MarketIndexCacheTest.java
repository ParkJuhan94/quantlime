package com.quantlime.market.cache;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.naver.NaverFinanceApiClient;
import com.quantlime.infra.naver.dto.NaverExchangeRateBasicResponse;
import com.quantlime.infra.naver.dto.NaverIndexBasicResponse;
import com.quantlime.infra.naver.exception.NaverFinanceApiErrorCode;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossExchangeRateResponse;
import com.quantlime.infra.toss.dto.TossExchangeRateResponse.ExchangeRateResult;
import com.quantlime.infra.tradingview.TradingViewApiClient;
import com.quantlime.infra.tradingview.dto.TradingViewSymbolResponse;
import com.quantlime.infra.upbit.UpbitApiClient;
import com.quantlime.infra.upbit.dto.UpbitTicker;
import com.quantlime.market.dto.response.MarketIndexResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarketIndexCacheTest {

    @Mock
    private TossApiClient tossApiClient;

    @Mock
    private UpbitApiClient upbitApiClient;

    @Mock
    private NaverFinanceApiClient naverFinanceApiClient;

    @Mock
    private TradingViewApiClient tradingViewApiClient;

    @InjectMocks
    private MarketIndexCache marketIndexCache;

    private void stubExchangeRateAndBitcoin() {
        given(tossApiClient.getExchangeRate("USD", "KRW")).willReturn(
            new TossExchangeRateResponse(new ExchangeRateResult("USD", "KRW", "1380.5", "UP")));
        given(upbitApiClient.getTicker("KRW-BTC")).willReturn(
            List.of(new UpbitTicker("KRW-BTC", 132000000L, 0.0345)));
    }

    @Test
    @DisplayName("[환율·비트코인 시세를 합쳐 응답을 만든다]")
    void get_firstCall_assemblesResponseFromBothSources() {
        // given
        given(tossApiClient.getExchangeRate("USD", "KRW")).willReturn(
            new TossExchangeRateResponse(new ExchangeRateResult("USD", "KRW", "1380.5", "UP")));
        given(upbitApiClient.getTicker("KRW-BTC")).willReturn(
            List.of(new UpbitTicker("KRW-BTC", 132000000L, 0.0345)));

        // when
        MarketIndexResponse result = marketIndexCache.get();

        // then
        assertThat(result.usdKrwRate()).isEqualTo(1380.5);
        assertThat(result.usdKrwChangeType()).isEqualTo("UP");
        assertThat(result.bitcoinPriceKrw()).isEqualTo(132000000L);
        assertThat(result.bitcoinChangeRate()).isEqualTo(3.45);
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 외부 API를 다시 호출하지 않는다]")
    void get_withinTtl_doesNotRefetch() {
        // given
        given(tossApiClient.getExchangeRate("USD", "KRW")).willReturn(
            new TossExchangeRateResponse(new ExchangeRateResult("USD", "KRW", "1380.5", "UP")));
        given(upbitApiClient.getTicker("KRW-BTC")).willReturn(
            List.of(new UpbitTicker("KRW-BTC", 132000000L, 0.0345)));

        // when
        marketIndexCache.get();
        marketIndexCache.get();

        // then
        verify(tossApiClient, times(1)).getExchangeRate(anyString(), anyString());
        verify(upbitApiClient, times(1)).getTicker(anyString());
    }

    @Test
    @DisplayName("[TTL이 지나면 다시 조회한다]")
    void get_afterTtlExpired_refetches() {
        // given
        given(tossApiClient.getExchangeRate("USD", "KRW")).willReturn(
            new TossExchangeRateResponse(new ExchangeRateResult("USD", "KRW", "1380.5", "UP")));
        given(upbitApiClient.getTicker("KRW-BTC")).willReturn(
            List.of(new UpbitTicker("KRW-BTC", 132000000L, 0.0345)));
        marketIndexCache.get();

        // when: 마지막 갱신 시각을 TTL 밖으로 되돌려 만료 상태를 재현
        ReflectionTestUtils.setField(marketIndexCache, "cachedAt", Instant.now().minusSeconds(21));
        marketIndexCache.get();

        // then
        verify(tossApiClient, times(2)).getExchangeRate(anyString(), anyString());
    }

    @Test
    @DisplayName("[코스피/코스닥 시세는 콤마를 제거해 파싱하고 marketStatus로 장중 여부를 판단한다]")
    void get_withNaverIndices_parsesKospiAndKosdaq() {
        // given
        stubExchangeRateAndBitcoin();
        given(naverFinanceApiClient.getIndexBasic("KOSPI")).willReturn(
            new NaverIndexBasicResponse("코스피", "7,284.41", "427.58", "6.24", "CLOSE", "2026-07-15T18:59:00+09:00", null));
        given(naverFinanceApiClient.getIndexBasic("KOSDAQ")).willReturn(
            new NaverIndexBasicResponse("코스닥", "829.43", "45.45", "5.80", "OPEN", "2026-07-15T14:00:00+09:00", null));

        // when
        MarketIndexResponse result = marketIndexCache.get();

        // then
        assertThat(result.kospi().value()).isEqualTo(7284.41);
        assertThat(result.kospi().changeAmount()).isEqualTo(427.58);
        assertThat(result.kospi().changeRate()).isEqualTo(6.24);
        assertThat(result.kospi().marketOpen()).isFalse();
        assertThat(result.kosdaq().marketOpen()).isTrue();
    }

    @Test
    @DisplayName("[네이버 금융 조회가 실패해도 환율·비트코인은 그대로 응답하고 지수는 null로 폴백한다]")
    void get_naverFails_fallsBackToNullIndicesWithoutBreakingOthers() {
        // given
        stubExchangeRateAndBitcoin();
        given(naverFinanceApiClient.getIndexBasic(anyString()))
            .willThrow(new ExternalApiException(NaverFinanceApiErrorCode.INDEX_INQUIRY_FAILED));

        // when
        MarketIndexResponse result = marketIndexCache.get();

        // then
        assertThat(result.usdKrwRate()).isEqualTo(1380.5);
        assertThat(result.kospi()).isNull();
        assertThat(result.kosdaq()).isNull();
    }

    @Test
    @DisplayName("[해외지수는 로이터 코드로, ETF(SOXX)는 종목 API로 조회하고 marketStatus로 장중 여부를 판단한다]")
    void get_withWorldIndices_parsesNasdaqSp500Soxx() {
        // given
        stubExchangeRateAndBitcoin();
        given(naverFinanceApiClient.getWorldIndexBasic(".IXIC")).willReturn(
            new NaverIndexBasicResponse(null, "26,201.58", "94.58", "0.36", "OPEN", null, null));
        given(naverFinanceApiClient.getWorldIndexBasic(".INX")).willReturn(
            new NaverIndexBasicResponse(null, "7,564.47", "20.88", "0.28", "OPEN", null, null));
        given(naverFinanceApiClient.getWorldStockBasic("SOXX.O")).willReturn(
            new NaverIndexBasicResponse(null, "555.27", "-12.65", "-2.23", "CLOSE", null,
                new NaverIndexBasicResponse.OverMarketPriceInfo("PRE_MARKET", "OPEN", "536.99", "-3.29")));

        // when
        MarketIndexResponse result = marketIndexCache.get();

        // then
        assertThat(result.nasdaq().value()).isEqualTo(26201.58);
        assertThat(result.nasdaq().marketOpen()).isTrue();
        assertThat(result.nasdaq().overMarketValue()).isNull();
        assertThat(result.sp500().value()).isEqualTo(7564.47);
        assertThat(result.soxx().changeAmount()).isEqualTo(-12.65);
        assertThat(result.soxx().marketOpen()).isFalse();
        assertThat(result.soxx().overMarketValue()).isEqualTo(536.99);
        assertThat(result.soxx().overMarketChangeRate()).isEqualTo(-3.29);
        assertThat(result.soxx().overMarketSessionType()).isEqualTo("PRE_MARKET");
    }

    @Test
    @DisplayName("[환율 등락률은 네이버로 보완하고, 실패해도 토스 기반 환율(rate)은 그대로 응답한다]")
    void get_usdKrwChangeRate_fromNaverWithResilientFallback() {
        // given
        stubExchangeRateAndBitcoin();
        given(naverFinanceApiClient.getExchangeRateBasic("FX_USDKRW")).willReturn(
            new NaverExchangeRateBasicResponse(
                new NaverExchangeRateBasicResponse.ExchangeInfo(
                    "1,480.40", "-8.10", "-0.54", "OPEN", "2026-07-16T21:32:53+09:00")));

        // when
        MarketIndexResponse result = marketIndexCache.get();

        // then
        assertThat(result.usdKrwChangeRate()).isEqualTo(-0.54);
    }

    @Test
    @DisplayName("[미국 10년물 국채금리는 TradingView로 조회하고, 실패해도 나머지 응답은 그대로 반환한다]")
    void get_usTreasuryYield_fromTradingViewWithResilientFallback() {
        // given
        stubExchangeRateAndBitcoin();
        given(tradingViewApiClient.getSymbolQuote("TVC:US10Y")).willReturn(
            new TradingViewSymbolResponse(4.59, 0.04));

        // when
        MarketIndexResponse result = marketIndexCache.get();

        // then
        assertThat(result.usTreasuryYield10y()).isEqualTo(4.59);
        assertThat(result.usTreasuryYield10yChangeRate()).isEqualTo(0.04);
        assertThat(result.usTreasuryYield10yHistory()).containsExactly(4.59);
    }

    @Test
    @DisplayName("[미국 10년물 국채금리 조회가 실패해도 나머지 응답은 그대로 반환한다]")
    void get_usTreasuryYieldFails_stillReturnsOtherFields() {
        // given
        stubExchangeRateAndBitcoin();
        given(tradingViewApiClient.getSymbolQuote(anyString()))
            .willThrow(new RuntimeException("boom"));

        // when
        MarketIndexResponse result = marketIndexCache.get();

        // then
        assertThat(result.usTreasuryYield10y()).isNull();
        assertThat(result.usTreasuryYield10yHistory()).isEmpty();
        assertThat(result.usdKrwRate()).isEqualTo(1380.5);
    }

    @Test
    @DisplayName("[환율 등락률 조회가 실패해도 토스 기반 환율(rate)·비트코인은 그대로 응답한다]")
    void get_usdKrwChangeRateFails_stillReturnsRateAndBitcoin() {
        // given
        stubExchangeRateAndBitcoin();
        given(naverFinanceApiClient.getExchangeRateBasic(anyString()))
            .willThrow(new ExternalApiException(NaverFinanceApiErrorCode.INDEX_INQUIRY_FAILED));

        // when
        MarketIndexResponse result = marketIndexCache.get();

        // then
        assertThat(result.usdKrwRate()).isEqualTo(1380.5);
        assertThat(result.usdKrwChangeRate()).isNull();
        assertThat(result.bitcoinPriceKrw()).isEqualTo(132000000L);
    }
}
