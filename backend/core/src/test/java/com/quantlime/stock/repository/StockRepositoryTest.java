package com.quantlime.stock.repository;

import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.support.DataJpaTestSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class StockRepositoryTest extends DataJpaTestSupport {

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        stockRepository.save(Stock.of("005930", "삼성전자", MarketType.KOSPI, ListingStatus.LISTED, "전기전자"));
        stockRepository.save(
            Stock.of("AAPL", "APPLE INC", MarketType.NASDAQ, ListingStatus.LISTED, "720", "애플"));
    }

    @Test
    @DisplayName("[해외 종목은 한글명으로도 검색된다]")
    void search_byKoreanName_findsOverseasStock() {
        // when
        Slice<Stock> result = stockRepository
            .findByStockNameContainingIgnoreCaseOrStockCodeContainingOrKoreanNameContainingIgnoreCase(
                "애플", "애플", "애플", PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).extracting(Stock::getStockCode).containsExactly("AAPL");
    }

    @Test
    @DisplayName("[해외 종목은 영문명으로도 검색된다]")
    void search_byEnglishName_findsOverseasStock() {
        // when
        Slice<Stock> result = stockRepository
            .findByStockNameContainingIgnoreCaseOrStockCodeContainingOrKoreanNameContainingIgnoreCase(
                "apple", "apple", "apple", PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).extracting(Stock::getStockCode).containsExactly("AAPL");
    }

    @Test
    @DisplayName("[해외 종목은 티커로도 검색된다]")
    void search_byTicker_findsOverseasStock() {
        // when
        Slice<Stock> result = stockRepository
            .findByStockNameContainingIgnoreCaseOrStockCodeContainingOrKoreanNameContainingIgnoreCase(
                "AAPL", "AAPL", "AAPL", PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).extracting(Stock::getStockCode).containsExactly("AAPL");
    }

    @Test
    @DisplayName("[국내 종목은 koreanName이 없어도 한글 종목명(stockName)으로 검색된다]")
    void search_domesticStock_matchesOnStockNameOnly() {
        // when
        Slice<Stock> result = stockRepository
            .findByStockNameContainingIgnoreCaseOrStockCodeContainingOrKoreanNameContainingIgnoreCase(
                "삼성전자", "삼성전자", "삼성전자", PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).extracting(Stock::getStockCode).containsExactly("005930");
    }

    @Test
    @DisplayName("[일치하는 종목이 없으면 빈 결과를 반환한다]")
    void search_noMatch_returnsEmpty() {
        // when
        Slice<Stock> result = stockRepository
            .findByStockNameContainingIgnoreCaseOrStockCodeContainingOrKoreanNameContainingIgnoreCase(
                "존재하지않음", "존재하지않음", "존재하지않음", PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).isEmpty();
    }
}
