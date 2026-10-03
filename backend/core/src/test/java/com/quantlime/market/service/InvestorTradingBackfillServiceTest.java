package com.quantlime.market.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.market.domain.AggregationInterval;
import com.quantlime.market.implement.InvestorTradingCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 어떤 시장·집계단위를 갱신할지(순회 대상)만 검증한다 - 조회·저장 자체는 InvestorTradingCollectorTest. */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class InvestorTradingBackfillServiceTest {

    @Mock
    private InvestorTradingCollector investorTradingCollector;

    @InjectMocks
    private InvestorTradingBackfillService investorTradingBackfillService;

    @Test
    @DisplayName("[KOSPI/KOSDAQ x 주간/월간 4개 조합을 모두 갱신한다]")
    void refreshAllIfNeeded_refreshesEveryMarketAndInterval() {
        investorTradingBackfillService.refreshAllIfNeeded();

        for (String marketCode : new String[] {"KOSPI", "KOSDAQ"}) {
            for (AggregationInterval interval : AggregationInterval.values()) {
                verify(investorTradingCollector).refresh(marketCode, interval);
            }
        }
        verify(investorTradingCollector, times(AggregationInterval.values().length * 2)).refresh(any(), any());
    }
}
