package com.quantlime.market.implement;

import com.quantlime.market.domain.InvestorTrading;
import com.quantlime.market.repository.InvestorTradingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 투자자별 매매대금 저장을 감싸는 구현 레이어(Implementation). */
@Component
@RequiredArgsConstructor
public class InvestorTradingAppender {

    private final InvestorTradingRepository investorTradingRepository;

    public void save(InvestorTrading investorTrading) {
        investorTradingRepository.save(investorTrading);
    }
}
