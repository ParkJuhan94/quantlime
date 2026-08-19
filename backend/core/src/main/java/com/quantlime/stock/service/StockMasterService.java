package com.quantlime.stock.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.stock.cache.StockSearchCache;
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
    private final StockSearchCache stockSearchCache;

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

    /**
     * 해외 종목마스터 동기화 전용({@link com.quantlime.stock.service.OverseasStockMasterSyncService}
     * 유일 호출부) - 이미 등록된 종목이면 한글명만 최신 값으로 백필하고
     * (주 1회 재동기화 때마다 KIS 마스터파일 값이 바뀌었을 수 있어), 나머지
     * 필드는 최초 등록 시점 값을 유지한다.
     */
    @Transactional
    public Stock registerStock(String stockCode, String stockName,
                               MarketType marketType, String sector, String koreanName) {
        var existing = stockRepository.findByStockCode(stockCode);
        if (existing.isPresent()) {
            Stock stock = existing.get();
            stock.updateKoreanName(koreanName);
            return stock;
        }

        Stock stock = Stock.of(stockCode, stockName, marketType,
            ListingStatus.LISTED, sector, koreanName);
        return stockRepository.save(stock);
    }

    /**
     * 가격 소스(Toss)가 커버하지 않는 종목으로 표시한다(stock-not-found 404
     * 반복 감지 시 {@link com.quantlime.market.service.MarketDataRefreshService}가
     * 호출). 이미 표시돼 있으면 no-op에 가깝고, 대상이 없으면 조용히 넘어간다.
     */
    @Transactional
    public void markPriceUnsupported(String stockCode) {
        stockRepository.findByStockCode(stockCode)
            .ifPresent(Stock::markPriceUnsupported);
    }

    @Transactional
    public void bulkRegisterStocks(List<Stock> stocks) {
        stockRepository.saveAll(stocks);
        log.info("종목 마스터 일괄 등록 완료: count={}", stocks.size());
    }

    public Slice<Stock> searchStocks(String keyword, Pageable pageable) {
        return stockSearchCache.search(keyword, pageable);
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
