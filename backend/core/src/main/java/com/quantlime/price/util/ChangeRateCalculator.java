package com.quantlime.price.util;

/**
 * "전일 종가 대비 등락률(%)" 계산을 국내 전종목 스윕(MarketPriceSweepScheduler)과
 * 해외 관심종목 랭킹/실시간가(MarketRankingService)가 각자 재구현하고
 * 있어 공용 유틸로 추출했다. 전일 종가가 없거나 0이면 등락률을 계산할
 * 수 없으므로 null을 반환한다(호출 측이 "등락률 미제공"으로 처리).
 */
public final class ChangeRateCalculator {

    private ChangeRateCalculator() {
    }

    public static Double calculate(Double currentPrice, Double previousClose) {
        if (previousClose == null || previousClose == 0) {
            return null;
        }
        return (currentPrice - previousClose) * 100.0 / previousClose;
    }
}
