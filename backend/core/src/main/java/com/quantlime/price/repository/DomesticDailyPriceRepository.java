package com.quantlime.price.repository;

import com.quantlime.price.domain.DomesticDailyPrice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomesticDailyPriceRepository
    extends JpaRepository<DomesticDailyPrice, Long>, DomesticDailyPriceQueryRepository {

    Optional<DomesticDailyPrice> findByStockCodeAndTradeDate(
        String stockCode, LocalDate tradeDate);

    List<DomesticDailyPrice> findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(
        String stockCode, LocalDate startDate, LocalDate endDate);

    List<DomesticDailyPrice> findByStockCodeInAndTradeDateBetweenOrderByTradeDateDesc(
        List<String> stockCodes, LocalDate startDate, LocalDate endDate);

    boolean existsByStockCodeAndTradeDate(
        String stockCode, LocalDate tradeDate);

    /**
     * {@code DomesticRegularCloseCaptureScheduler}가 정규장 종가 캡처 대상
     * 종목 전체(~2,700개)의 "오늘 행 존재 여부"를 배치 조회할 때 쓴다 - 종목당
     * 개별 조회 대신 한 번의 IN 쿼리로 왕복을 줄인다.
     */
    List<DomesticDailyPrice> findByStockCodeInAndTradeDate(
        List<String> stockCodes, LocalDate tradeDate);

    long countByStockCode(String stockCode);

    Optional<DomesticDailyPrice> findTopByStockCodeOrderByTradeDateDesc(String stockCode);
}
