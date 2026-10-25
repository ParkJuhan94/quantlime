package com.quantlime.tradingflow.implement;

import com.quantlime.tradingflow.domain.StockInvestorTrading;
import com.quantlime.tradingflow.repository.StockInvestorTradingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockInvestorTradingReader {

    private final StockInvestorTradingRepository stockInvestorTradingRepository;

    /** 종목의 최신 {@code count}건을 최신순으로 돌려준다. */
    public List<StockInvestorTrading> findLatest(String stockCode, int count) {
        return stockInvestorTradingRepository.findByStockCodeOrderByTradeDateDesc(stockCode, PageRequest.of(0, count));
    }

    public List<StockInvestorTrading> findBetween(String stockCode, LocalDate from, LocalDate to) {
        return stockInvestorTradingRepository.findByStockCodeAndTradeDateBetween(stockCode, from, to);
    }

    public long countByStockCode(String stockCode) {
        return stockInvestorTradingRepository.countByStockCode(stockCode);
    }

    /** 전 종목을 통틀어 저장된 가장 최근 거래일 - 서버가 꺼져 있던 동안 놓친 갱신이 있는지 판단하는 데 쓴다. */
    public Optional<LocalDate> findLatestTradeDate() {
        return stockInvestorTradingRepository.findLatestTradeDate();
    }
}
