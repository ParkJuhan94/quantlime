package com.quantlime.tradingflow.implement;

import com.quantlime.tradingflow.domain.StockInvestorTrading;
import com.quantlime.tradingflow.repository.StockInvestorTradingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockInvestorTradingAppender {

    private final StockInvestorTradingRepository stockInvestorTradingRepository;

    public void save(StockInvestorTrading stockInvestorTrading) {
        stockInvestorTradingRepository.save(stockInvestorTrading);
    }
}
