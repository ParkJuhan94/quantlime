package com.quantlab.market.cache;

import com.quantlab.common.exception.NotFoundException;
import com.quantlab.infra.naver.NaverFinanceApiClient;
import com.quantlab.infra.naver.dto.NaverIndexBasicResponse;
import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossExchangeRateResponse;
import com.quantlab.infra.tradingview.TradingViewApiClient;
import com.quantlab.infra.tradingview.dto.TradingViewSymbolResponse;
import com.quantlab.infra.upbit.UpbitApiClient;
import com.quantlab.infra.upbit.dto.UpbitTicker;
import com.quantlab.market.domain.WorldIndexCode;
import com.quantlab.market.dto.response.MarketIndexResponse;
import com.quantlab.market.exception.MarketErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 환율(토스)·비트코인(Upbit)·코스피/코스닥(네이버 금융) 시세를 짧은
 * TTL로 캐싱한다. 토스 환율 자체는 스펙상 1분 단위로만 갱신되지만,
 * 같은 응답에 묶인 코스피/코스닥/해외지수/비트코인은 훨씬 자주 바뀌므로
 * TTL을 {@value #TTL_SECONDS}초로 짧게 잡는다 - 환율만 갱신 없이 같은 값을
 * 반복 반환할 뿐 나머지 값들의 체감 실시간성이 개선된다(2026-07-16,
 * 20초→8초→5초로 단계적으로 단축. 한 번 갱신에 네이버 5회·TradingView
 * 1회·Toss 1회·Upbit 1회, 총 8개 외부 호출이 묶여 있어 너무 짧게 잡으면
 * 비공식 스크래핑 대상(네이버·TradingView)이 IP 차단할 위험이 있다 -
 * 정확한 공개 레이트리밋 문서가 없어 "체감 실시간성 대 차단 위험"을
 * 저울질한 보수적 값. 429/차단 징후 없이 안정적이면 더 낮춰도 됨)
 * (MarketCalendarCache와 동일한 단순 TTL 캐시 패턴).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketIndexCache {

    private static final int TTL_SECONDS = 5;
    private static final String USD = "USD";
    private static final String KRW = "KRW";
    private static final String USD_KRW_PAIR = "FX_USDKRW";
    private static final String BITCOIN_MARKET = "KRW-BTC";
    private static final String KOSPI_CODE = "KOSPI";
    private static final String KOSDAQ_CODE = "KOSDAQ";
    private static final String MARKET_STATUS_OPEN = "OPEN";
    private static final String US_10Y_TREASURY_SYMBOL = "TVC:US10Y";
    private static final int TREASURY_YIELD_HISTORY_SIZE = 30;

    private final TossApiClient tossApiClient;
    private final UpbitApiClient upbitApiClient;
    private final NaverFinanceApiClient naverFinanceApiClient;
    private final TradingViewApiClient tradingViewApiClient;

    private volatile MarketIndexResponse cached;
    private volatile Instant cachedAt = Instant.EPOCH;
    // refresh()가 synchronized라 이 필드도 항상 그 안에서만 변경된다 -
    // cached에 담기는 건 매번 새로 뜬 불변 스냅샷(List.copyOf)이라 다른
    // 스레드가 cached를 읽는 동안 이 큐 자체가 동시에 수정될 일은 없다.
    private final Deque<Double> treasuryYieldHistory = new ArrayDeque<>();

    public MarketIndexResponse get() {
        if (isStale()) {
            refresh();
        }
        return cached;
    }

    private boolean isStale() {
        return cached == null || Duration.between(cachedAt, Instant.now()).getSeconds() >= TTL_SECONDS;
    }

    private synchronized void refresh() {
        if (!isStale()) {
            return; // 락 대기 중 다른 스레드가 이미 갱신함
        }
        TossExchangeRateResponse.ExchangeRateResult exchangeRate =
            tossApiClient.getExchangeRate(USD, KRW).result();
        UpbitTicker bitcoinTicker = getBitcoinTicker();
        TradingViewSymbolResponse treasuryYield = fetchUsTreasuryYield();
        recordTreasuryYieldHistory(treasuryYield);

        cached = new MarketIndexResponse(
            Double.parseDouble(exchangeRate.rate()),
            exchangeRate.rateChangeType(),
            fetchUsdKrwChangeRate(),
            bitcoinTicker.tradePrice(),
            bitcoinTicker.signedChangeRate() * 100,
            treasuryYield != null ? treasuryYield.close() : null,
            treasuryYield != null ? treasuryYield.changeRate() : null,
            List.copyOf(treasuryYieldHistory),
            fetchIndexQuote(KOSPI_CODE),
            fetchIndexQuote(KOSDAQ_CODE),
            fetchWorldIndexQuote(WorldIndexCode.NASDAQ),
            fetchWorldIndexQuote(WorldIndexCode.SP500),
            fetchWorldIndexQuote(WorldIndexCode.SOXX)
        );
        cachedAt = Instant.now();
    }

    /** TradingView 조회 실패는 흡수하고 null 반환 - 다른 지수와 동일한 폴백 원칙. */
    private TradingViewSymbolResponse fetchUsTreasuryYield() {
        try {
            return tradingViewApiClient.getSymbolQuote(US_10Y_TREASURY_SYMBOL);
        } catch (Exception e) {
            log.warn("TradingView 미국 10년물 국채금리 조회 실패: error={}", e.getMessage());
            return null;
        }
    }

    /**
     * TradingView 공개 API엔 과거 시세 조회가 없고(실제 호출로 404 확인),
     * FRED 같은 공식 소스는 Akamai Bot Manager로 추정되는 봇 차단에
     * JDK HttpClient가 걸려 타임아웃만 반복돼(2026-07-18, User-Agent·
     * HTTP/1.1 강제로도 우회 안 됨 - TLS 핑거프린팅 기반 차단으로 추정)
     * 포기했다 - 대신 갱신될 때마다 값을 최대 {@value #TREASURY_YIELD_HISTORY_SIZE}
     * 개까지 누적해 미니 차트 데이터로 쓴다. 이 값은 실제 일별 시세가
     * 아니라 폴링 시점 스냅샷이 쌓인 것이라(짧으면 수 분 단위) 홈 카드
     * 미니 차트에서만 쓰고, 일봉 단위를 전제하는 상세 페이지 차트에는
     * 쓰지 않는다(IndexDetailPage 참고 - 오해를 부를 수 있어 상세
     * 페이지에서는 아예 차트를 비워둔다). 조회 실패 시에는 추가하지
     * 않고 마지막까지 쌓인 이력을 그대로 유지한다.
     */
    private void recordTreasuryYieldHistory(TradingViewSymbolResponse treasuryYield) {
        if (treasuryYield == null || treasuryYield.close() == null) {
            return;
        }
        treasuryYieldHistory.addLast(treasuryYield.close());
        if (treasuryYieldHistory.size() > TREASURY_YIELD_HISTORY_SIZE) {
            treasuryYieldHistory.removeFirst();
        }
    }

    /** 네이버 조회 실패는 흡수하고 null 반환 - 토스 기반 환율(rate)은 이미 확보돼 있으므로
     * 등락률(%) 하나 못 채운다고 환율 카드 전체가 깨지면 안 된다. */
    private Double fetchUsdKrwChangeRate() {
        try {
            return parseNumber(naverFinanceApiClient.getExchangeRateBasic(USD_KRW_PAIR).exchangeInfo().fluctuationsRatio());
        } catch (Exception e) {
            log.warn("네이버 금융 환율 등락률 조회 실패: error={}", e.getMessage());
            return null;
        }
    }

    private UpbitTicker getBitcoinTicker() {
        List<UpbitTicker> tickers = upbitApiClient.getTicker(BITCOIN_MARKET);
        return tickers.stream()
            .findFirst()
            .orElseThrow(() -> new NotFoundException(MarketErrorCode.BITCOIN_TICKER_NOT_FOUND));
    }

    /**
     * 네이버 금융은 문서화되지 않은 비공식 API라(토스에 지수 심볼이
     * 없어 대안으로 씀) 환율·비트코인과 달리 실패를 전파하지 않고
     * null로 흡수한다 - 이미 잘 동작하던 나머지 위젯까지 이 호출
     * 하나 때문에 통째로 깨지면 안 된다. 프론트는 null이면 예시
     * 데이터로 폴백한다.
     */
    private MarketIndexResponse.IndexQuote fetchIndexQuote(String indexCode) {
        try {
            NaverIndexBasicResponse basic = naverFinanceApiClient.getIndexBasic(indexCode);
            double value = parseNumber(basic.closePrice());
            double changeAmount = parseNumber(basic.compareToPreviousClosePrice());
            double changeRate = parseNumber(basic.fluctuationsRatio());
            boolean marketOpen = MARKET_STATUS_OPEN.equalsIgnoreCase(basic.marketStatus());
            // 국내 지수는 프리/애프터마켓 개념이 없다(응답 필드 자체가 없음).
            return new MarketIndexResponse.IndexQuote(value, changeAmount, changeRate, marketOpen, null, null, null);
        } catch (Exception e) {
            log.warn("네이버 금융 지수 조회 실패, 예시 데이터로 폴백: indexCode={}, error={}",
                indexCode, e.getMessage());
            return null;
        }
    }

    /** 해외지수는 조회 방식만 다를 뿐(로이터 코드, api.stock.naver.com)
     * 응답 파싱은 국내 지수와 동일하다 - 실패 시 null 폴백도 동일. SOXX처럼
     * ETF인 경우 지수 엔드포인트가 아니라 종목 엔드포인트를 쓴다. 미국
     * 시장이라 정규장 마감 중엔 overMarketPriceInfo로 프리/애프터마켓
     * 시세가 함께 온다(실제 호출로 확인).  */
    private MarketIndexResponse.IndexQuote fetchWorldIndexQuote(WorldIndexCode code) {
        try {
            NaverIndexBasicResponse basic = code.isEtf()
                ? naverFinanceApiClient.getWorldStockBasic(code.getReutersCode())
                : naverFinanceApiClient.getWorldIndexBasic(code.getReutersCode());
            double value = parseNumber(basic.closePrice());
            double changeAmount = parseNumber(basic.compareToPreviousClosePrice());
            double changeRate = parseNumber(basic.fluctuationsRatio());
            boolean marketOpen = MARKET_STATUS_OPEN.equalsIgnoreCase(basic.marketStatus());
            NaverIndexBasicResponse.OverMarketPriceInfo overMarket = basic.overMarketPriceInfo();
            Double overMarketValue = overMarket != null ? parseNumber(overMarket.overPrice()) : null;
            Double overMarketChangeRate = overMarket != null ? parseNumber(overMarket.fluctuationsRatio()) : null;
            String overMarketSessionType = overMarket != null ? overMarket.tradingSessionType() : null;
            return new MarketIndexResponse.IndexQuote(
                value, changeAmount, changeRate, marketOpen,
                overMarketValue, overMarketChangeRate, overMarketSessionType);
        } catch (Exception e) {
            log.warn("네이버 금융 해외지수 조회 실패: code={}, error={}", code, e.getMessage());
            return null;
        }
    }

    private double parseNumber(String raw) {
        return Double.parseDouble(raw.replace(",", ""));
    }
}
