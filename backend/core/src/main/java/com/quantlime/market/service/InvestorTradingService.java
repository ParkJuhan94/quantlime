package com.quantlime.market.service;

import com.quantlime.market.domain.AggregationInterval;
import com.quantlime.market.domain.InvestorTrading;
import com.quantlime.market.dto.mapper.InvestorTradingMapper;
import com.quantlime.market.dto.response.InvestorTradingResponse;
import com.quantlime.market.repository.InvestorTradingRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 투자자별 매매대금(순매수) 조회 - 외부 API 호출 없이 영속 저장된
 * {@link InvestorTrading}만 읽는다(적재는 {@link InvestorTradingBackfillService} 담당).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestorTradingService {

    private final InvestorTradingRepository investorTradingRepository;

    public List<InvestorTradingResponse> getInvestorTrading(String marketCode, String intervalQueryValue, int count) {
        AggregationInterval interval = AggregationInterval.fromQueryValue(intervalQueryValue);
        List<InvestorTrading> latestFirst = investorTradingRepository
            .findByMarketCodeAndAggregationIntervalOrderByBaseDateDesc(
                marketCode, interval, PageRequest.of(0, count));

        List<InvestorTradingResponse> responses = new ArrayList<>(latestFirst.stream()
            .map(InvestorTradingMapper::toInvestorTradingResponse)
            .toList());
        Collections.reverse(responses); // 차트/표 소비 관례상 오름차순(과거→최신)으로 반환
        return responses;
    }
}
