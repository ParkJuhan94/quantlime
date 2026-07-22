package com.quantlime.stock.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.exception.StockErrorCode;
import com.quantlime.stock.repository.StockRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    /**
     * 주어진 종목 코드 순서 그대로 종목을 반환한다(호출 측이 이미 특정
     * 기준으로 정렬해둔 코드 목록을 그대로 유지해야 할 때 사용 - 인기
     * 종목처럼 "IN" 조회 결과가 입력 순서를 보장하지 않는 경우를 보완).
     */
    @Transactional(readOnly = true)
    public List<Stock> getStocksByCodesInOrder(List<String> stockCodes) {
        Map<String, Stock> stockByCode = stockRepository.findByStockCodeIn(stockCodes).stream()
            .collect(Collectors.toMap(Stock::getStockCode, Function.identity()));
        return stockCodes.stream()
            .map(stockByCode::get)
            .filter(Objects::nonNull)
            .toList();
    }
}
