package com.quantlime.market.service;

import com.quantlime.market.domain.AggregationInterval;
import com.quantlime.market.implement.InvestorTradingCollector;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 코스피/코스닥 투자자별 매매대금(주간/월간)을 어떤 시장·집계단위에 대해 갱신할지 정한다 -
 * Toss 호출·변환·upsert는 {@link InvestorTradingCollector}가 맡는다. 매번 최신 100건을
 * 다시 조회해 당일/당주/당월처럼 장 종료 전까지 계속 갱신되는 잠정치를 놓치지 않는다
 * ("이미 충분히 쌓였으면 스킵"하는 {@code BenchmarkIndexBackfillService}와 다른 점).
 */
@Service
@RequiredArgsConstructor
public class InvestorTradingBackfillService {

    private static final List<String> MARKET_CODES = List.of("KOSPI", "KOSDAQ");

    private final InvestorTradingCollector investorTradingCollector;

    public void refreshAllIfNeeded() {
        for (String marketCode : MARKET_CODES) {
            for (AggregationInterval interval : AggregationInterval.values()) {
                investorTradingCollector.refresh(marketCode, interval);
            }
        }
    }
}
