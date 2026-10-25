package com.quantlime.tradingflow.service;

import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import com.quantlime.tradingflow.domain.StockInvestorTrading;
import com.quantlime.tradingflow.dto.mapper.StockInvestorTradingMapper;
import com.quantlime.tradingflow.dto.response.StockInvestorTradingResponse;
import com.quantlime.tradingflow.implement.StockInvestorTradingReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockInvestorTradingService {

    private final StockMasterService stockMasterService;
    private final StockInvestorTradingReader stockInvestorTradingReader;

    /**
     * 종목의 최근 {@code count}거래일 투자자 매매동향을 오름차순으로 돌려준다. 해외 종목은 토스가 매매동향을 제공하지
     * 않아(국내 전용) 400이 아니라 빈 목록을 돌려준다 - 프론트가 영역 자체를 숨기는 신호로 쓴다.
     */
    @Transactional(readOnly = true)
    public List<StockInvestorTradingResponse> getRecent(String stockCode, int count) {
        Stock stock = stockMasterService.getStockByCode(stockCode);
        if (!stock.getMarketType().isDomestic()) {
            return List.of();
        }
        List<StockInvestorTrading> rows =
            new ArrayList<>(stockInvestorTradingReader.findLatest(stock.getStockCode(), count));
        Collections.reverse(rows);
        return rows.stream().map(StockInvestorTradingMapper::toStockInvestorTradingResponse).toList();
    }
}
