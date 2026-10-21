package com.quantlime.market.dto.response;

import java.util.List;
import org.springframework.lang.Nullable;

public record MarketIndexResponse(
    Double usdKrwRate,
    String usdKrwChangeType,
    @Nullable // 토스 환율 API는 rate/changeType만 주고 등락률(%)이 없어 네이버로
    // 보완한다 - 네이버 조회가 실패해도(비공식 API) 토스 기반 환율 자체는
    // 그대로 응답해야 하므로 이 필드만 null 허용.
    Double usdKrwChangeRate,
    Long bitcoinPriceKrw,
    Double bitcoinChangeRate,
    // 토스·네이버 어디에도 없는 심볼이라 TradingView 공개 스캐너 API로
    // 조회한다 - 실패 시 null(다른 지수와 동일한 폴백 원칙).
    @Nullable Double usTreasuryYield10y,
    @Nullable Double usTreasuryYield10yChangeRate,
    // FRED 등 공식 과거 시세 소스는 Akamai로 추정되는 봇 차단에 걸려
    // 포기했다(MarketIndexCache.recordTreasuryYieldHistory 참고) - 대신
    // 백엔드가 폴링할 때마다 값을 누적한 이력을 홈 카드 미니 차트에만
    // 쓴다. 다른 카드의 차트(실제 일봉 이력)보다 훨씬 짧고(재시작 시
    // 비어 있음, 몇 분 단위) 일봉이 아니라는 한계는 있지만, 값을
    // 지어내지 않는다는 원칙은 지킨다.
    List<Double> usTreasuryYield10yHistory,
    @Nullable IndexQuote kospi,
    @Nullable IndexQuote kosdaq,
    @Nullable IndexQuote nasdaq,
    @Nullable IndexQuote sp500,
    @Nullable IndexQuote soxx
) {

    // 네이버 금융 조회가 실패해도(비공식 API라 언제든 막힐 수 있음) 환율·
    // 비트코인은 그대로 응답하도록 지수 필드는 전부 null 허용 - 프론트는
    // null이면 자리만 유지하고 값을 비워 보여준다.
    public record IndexQuote(
        Double value,
        @Nullable Double changeAmount,
        @Nullable Double changeRate,
        boolean marketOpen,
        // 미국 시장 카드에서만 채워진다(정규장 마감 중 프리/애프터마켓이
        // 열려 있을 때). 국내 지수는 항상 null.
        @Nullable Double overMarketValue,
        @Nullable Double overMarketChangeRate,
        @Nullable String overMarketSessionType
    ) {
    }
}
