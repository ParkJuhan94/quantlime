package com.quantlab.stock.repository;

import com.quantlab.stock.domain.ListingStatus;
import com.quantlab.stock.domain.Stock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByStockCode(String stockCode);

    List<Stock> findByListingStatus(ListingStatus listingStatus);

    boolean existsByStockCode(String stockCode);

    Slice<Stock> findByStockNameContainingIgnoreCaseOrStockCodeContaining(
        String stockName, String stockCode, Pageable pageable);
}
