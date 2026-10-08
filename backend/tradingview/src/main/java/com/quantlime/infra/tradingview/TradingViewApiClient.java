package com.quantlime.infra.tradingview;

import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.infra.tradingview.dto.TradingViewSymbolResponse;
import com.quantlime.infra.tradingview.exception.TradingViewApiErrorCode;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * TradingView의 공개 스캐너 API(인증 불필요, 위젯이 쓰는 것과 동일한
 * 엔드포인트 - 실제 호출로 확인). 미국 국채금리처럼 토스·네이버 어디에도
 * 없는 심볼을 위해서만 제한적으로 연동한다(종목 로고·지수 연동과 동일한
 * 판단 - StockMapper, NaverFinanceApiClient 참고). 인베스팅닷컴은 Cloudflare
 * 봇 차단이 걸려 있어 서버 간 호출 자체가 불가능함을 확인, 대안으로
 * TradingView를 채택했다.
 *
 * <p>진입점에 {@code @CircuitBreaker}/{@code @Bulkhead}("tradingview" 인스턴스)
 * 적용(2026-09-24) - naver-finance/upbit와 동일한 패턴. 별도 폴백 메서드는
 * 두지 않는다 - 유일한 호출측인 MarketIndexCache가 이미 모든 예외를
 * {@code catch (Exception e)}로 넓게 잡아 이전 캐시값으로 stale-serve하므로,
 * 서킷 open 시 던져지는 CallNotPermittedException도 그대로 흡수된다.
 */
@Component
@RequiredArgsConstructor
public class TradingViewApiClient {

    private final RestClient tradingViewRestClient;

    @Retry(name = "tradingview")
    @CircuitBreaker(name = "tradingview")
    @Bulkhead(name = "tradingview")
    public TradingViewSymbolResponse getSymbolQuote(String symbol) {
        return ExternalApiInvoker.call(
            TradingViewApiErrorCode.SYMBOL_QUOTE_INQUIRY_FAILED,
            () -> tradingViewRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/symbol")
                    .queryParam("symbol", symbol)
                    .queryParam("fields", "close,change")
                    .build())
                .retrieve()
                .body(TradingViewSymbolResponse.class));
    }
}
