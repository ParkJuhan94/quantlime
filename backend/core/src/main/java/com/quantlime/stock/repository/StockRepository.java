package com.quantlime.stock.repository;

import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByStockCode(String stockCode);

    List<Stock> findByListingStatus(ListingStatus listingStatus);

    List<Stock> findByListingStatusAndMarketTypeIn(ListingStatus listingStatus, List<MarketType> marketTypes);

    List<Stock> findByListingStatusAndMarketTypeInAndPriceUnsupportedFalse(
        ListingStatus listingStatus, List<MarketType> marketTypes);

    boolean existsByStockCode(String stockCode);

    // 해외 종목은 stockName에 영문명, koreanName에 한글명이 따로 저장돼
    // 있어(국내는 koreanName이 항상 null) 검색어가 셋 중 어디에 걸리든
    // 찾을 수 있게 OR로 묶는다(2026-08-01 - 한글/영문/티커 통합 검색 요청).
    Slice<Stock> findByStockNameContainingIgnoreCaseOrStockCodeContainingOrKoreanNameContainingIgnoreCase(
        String stockName, String stockCode, String koreanName, Pageable pageable);

    List<Stock> findByStockCodeIn(List<String> stockCodes);
}
