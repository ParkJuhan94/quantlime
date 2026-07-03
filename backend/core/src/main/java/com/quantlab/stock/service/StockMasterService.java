package com.quantlab.stock.service;

import com.quantlab.common.exception.NotFoundException;
import com.quantlab.stock.domain.ListingStatus;
import com.quantlab.stock.domain.MarketType;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.exception.StockErrorCode;
import com.quantlab.stock.repository.StockRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMasterService {

    private final StockRepository stockRepository;

    @Transactional(readOnly = true)
    public List<Stock> getAllListedStocks() {
        return stockRepository.findByListingStatus(ListingStatus.LISTED);
    }

    @Transactional(readOnly = true)
    public Stock getStockByCode(String stockCode) {
        return stockRepository.findByStockCode(stockCode)
            .orElseThrow(() -> new NotFoundException(
                StockErrorCode.NOT_FOUND_STOCK));
    }

    @Transactional
    public Stock registerStock(String stockCode, String stockName,
                               MarketType marketType, String sector) {
        if (stockRepository.existsByStockCode(stockCode)) {
            log.debug("이미 등록된 종목: stockCode={}", stockCode);
            return stockRepository.findByStockCode(stockCode).get();
        }

        Stock stock = Stock.of(stockCode, stockName, marketType,
            ListingStatus.LISTED, sector);
        return stockRepository.save(stock);
    }

    @Transactional
    public void bulkRegisterStocks(List<Stock> stocks) {
        stockRepository.saveAll(stocks);
        log.info("종목 마스터 일괄 등록 완료: count={}", stocks.size());
    }

    @Transactional(readOnly = true)
    public Slice<Stock> searchStocks(String keyword, Pageable pageable) {
        String trimmedKeyword = keyword.trim();
        return stockRepository.findByStockNameContainingIgnoreCaseOrStockCodeContaining(
            trimmedKeyword, trimmedKeyword, pageable);
    }
}
