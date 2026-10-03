package com.quantlime.price.implement;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.dto.DomesticStockTradingValue;
import com.quantlime.price.dto.LiquiditySnapshot;
import com.quantlime.price.dto.OverseasStockTradingValue;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 국내/해외 일봉 조회를 감싸는 구현 레이어(Implementation) - 다른 도메인(market)의
 * 서비스가 price의 Repository를 직접 잡지 않고 이 컴포넌트를 통해서만 거래대금
 * 순위와 최신 저장일을 읽게 한다.
 */
@Component
@RequiredArgsConstructor
public class DailyPriceReader {

    private final DomesticDailyPriceRepository domesticDailyPriceRepository;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;

    /** 거래대금 내림차순 국내 종목코드 - 갱신 순서 결정용(전체 순위). */
    public List<String> findDomesticCodesByTradingValueDesc(LocalDate since) {
        return domesticDailyPriceRepository.findStockCodesOrderedByTradingValueDesc(since);
    }

    public List<String> findOverseasCodesByTradingValueDesc(LocalDate since) {
        return overseasDailyPriceRepository.findStockCodesOrderedByTradingValueDesc(since);
    }

    /** 거래대금 상위 N개 국내 종목 - 백테스트 유니버스 선정용. */
    public List<DomesticStockTradingValue> findTopDomesticByTradingValue(LocalDate since, int limit) {
        return domesticDailyPriceRepository.findTopByTradingValue(since, limit);
    }

    public List<OverseasStockTradingValue> findTopOverseasByTradingValue(LocalDate since, int limit) {
        return overseasDailyPriceRepository.findTopByTradingValue(since, limit);
    }

    public Optional<LocalDate> findLatestDomesticTradeDate(String stockCode) {
        return domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(DomesticDailyPrice::getTradeDate);
    }

    public Optional<LocalDate> findLatestOverseasTradeDate(String stockCode) {
        return overseasDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(OverseasDailyPrice::getTradeDate);
    }

    // ---- 국내 일봉 조회

    public Optional<DomesticDailyPrice> findDomestic(String stockCode, LocalDate tradeDate) {
        return domesticDailyPriceRepository.findByStockCodeAndTradeDate(stockCode, tradeDate);
    }

    /** 특정 거래일의 국내 일봉을 종목코드 목록으로 한 번에 조회한다. */
    public List<DomesticDailyPrice> findDomesticByCodesAndDate(List<String> stockCodes, LocalDate tradeDate) {
        return domesticDailyPriceRepository.findByStockCodeInAndTradeDate(stockCodes, tradeDate);
    }

    public boolean existsDomestic(String stockCode, LocalDate tradeDate) {
        return domesticDailyPriceRepository.existsByStockCodeAndTradeDate(stockCode, tradeDate);
    }

    public long countDomestic(String stockCode) {
        return domesticDailyPriceRepository.countByStockCode(stockCode);
    }

    public Optional<DomesticDailyPrice> findLatestDomestic(String stockCode) {
        return domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode);
    }

    /** 기간 내 일봉, 최신순. */
    public List<DomesticDailyPrice> findDomesticBetween(String stockCode, LocalDate from, LocalDate to) {
        return domesticDailyPriceRepository
            .findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, from, to);
    }

    public List<DomesticDailyPrice> findDomesticBetweenForCodes(
        List<String> stockCodes, LocalDate from, LocalDate to) {
        return domesticDailyPriceRepository
            .findByStockCodeInAndTradeDateBetweenOrderByTradeDateDesc(stockCodes, from, to);
    }

    /** 종목별로 {@code date} 이전의 가장 최근 일봉. */
    public List<DomesticDailyPrice> findDomesticLatestBefore(List<String> stockCodes, LocalDate date) {
        return domesticDailyPriceRepository.findLatestBeforeDate(stockCodes, date);
    }

    public List<LiquiditySnapshot> findDomesticLiquiditySnapshot(LocalDate since) {
        return domesticDailyPriceRepository.findLiquiditySnapshot(since);
    }

    // ---- 해외 일봉 조회

    public Optional<OverseasDailyPrice> findOverseas(String stockCode, LocalDate tradeDate) {
        return overseasDailyPriceRepository.findByStockCodeAndTradeDate(stockCode, tradeDate);
    }

    public boolean existsOverseas(String stockCode, LocalDate tradeDate) {
        return overseasDailyPriceRepository.existsByStockCodeAndTradeDate(stockCode, tradeDate);
    }

    public long countOverseas(String stockCode) {
        return overseasDailyPriceRepository.countByStockCode(stockCode);
    }

    public Optional<OverseasDailyPrice> findLatestOverseas(String stockCode) {
        return overseasDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode);
    }

    public List<OverseasDailyPrice> findOverseasBetween(String stockCode, LocalDate from, LocalDate to) {
        return overseasDailyPriceRepository
            .findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, from, to);
    }

    public List<OverseasDailyPrice> findOverseasLatestBefore(List<String> stockCodes, LocalDate date) {
        return overseasDailyPriceRepository.findLatestBeforeDate(stockCodes, date);
    }

    public List<LiquiditySnapshot> findOverseasLiquiditySnapshot(LocalDate since) {
        return overseasDailyPriceRepository.findLiquiditySnapshot(since);
    }
}
