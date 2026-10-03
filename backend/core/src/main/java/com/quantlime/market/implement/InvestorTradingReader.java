package com.quantlime.market.implement;

import com.quantlime.market.domain.AggregationInterval;
import com.quantlime.market.domain.InvestorTrading;
import com.quantlime.market.repository.InvestorTradingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** 투자자별 매매대금 조회를 감싸는 구현 레이어(Implementation). */
@Component
@RequiredArgsConstructor
public class InvestorTradingReader {

    private final InvestorTradingRepository investorTradingRepository;

    public Optional<InvestorTrading> find(String marketCode, AggregationInterval interval, LocalDate baseDate) {
        return investorTradingRepository
            .findByMarketCodeAndAggregationIntervalAndBaseDate(marketCode, interval, baseDate);
    }

    /** 최신순 최대 {@code count}건. */
    public List<InvestorTrading> findLatest(String marketCode, AggregationInterval interval, int count) {
        return investorTradingRepository
            .findByMarketCodeAndAggregationIntervalOrderByBaseDateDesc(
                marketCode, interval, PageRequest.of(0, count));
    }
}
