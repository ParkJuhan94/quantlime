package com.quantlime.price.repository;

import com.quantlime.price.domain.DomesticRegularClosePrice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomesticRegularClosePriceRepository
    extends JpaRepository<DomesticRegularClosePrice, Long>, DomesticRegularClosePriceQueryRepository {

    Optional<DomesticRegularClosePrice> findByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    boolean existsByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    /**
     * 특정 날짜에 이미 캡처된 종목코드 전체 - {@code
     * DomesticRegularCloseCaptureScheduler}가 국내 전상장종목(~2,700개)을
     * 돌 때 종목마다 existsByStockCodeAndTradeDate를 부르는 대신 이 배치
     * 조회 한 번으로 "이미 캡처된 집합"을 얻어 멱등 판단에 쓴다(2026-09
     * 성능 감사).
     */
    List<String> findStockCodeByTradeDate(LocalDate tradeDate);
}
