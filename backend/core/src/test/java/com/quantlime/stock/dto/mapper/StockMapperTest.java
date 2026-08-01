package com.quantlime.stock.dto.mapper;

import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.dto.response.StockDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class StockMapperTest {

    @Test
    @DisplayName("[국내 종목은 기존과 동일하게 종목코드 그대로 로고 URL을 만든다]")
    void toStockDetailResponse_domestic_buildsLogoUrlFromStockCode() {
        // given
        Stock stock = Stock.of("005930", "삼성전자", MarketType.KOSPI, ListingStatus.LISTED, "전기전자");

        // when
        StockDetailResponse response = StockMapper.toStockDetailResponse(stock);

        // then
        assertThat(response.stockName()).isEqualTo("삼성전자");
        assertThat(response.logoUrl())
            .isEqualTo("https://ssl.pstatic.net/imgstock/fn/real/logo/png/stock/Stock005930.png");
    }

    @Test
    @DisplayName("[나스닥 종목은 한글명을 우선 노출하고 로고 URL에 .O 접미사를 붙인다]")
    void toStockDetailResponse_nasdaq_usesKoreanNameAndAddsSuffixToLogoUrl() {
        // given
        Stock stock = Stock.of("AAPL", "APPLE INC", MarketType.NASDAQ, ListingStatus.LISTED, "720", "애플");

        // when
        StockDetailResponse response = StockMapper.toStockDetailResponse(stock);

        // then
        assertThat(response.stockName()).isEqualTo("애플");
        assertThat(response.logoUrl())
            .isEqualTo("https://ssl.pstatic.net/imgstock/fn/real/logo/png/stock/StockAAPL.O.png");
    }

    @Test
    @DisplayName("[뉴욕증권거래소 종목은 로고 URL에 접미사를 붙이지 않는다]")
    void toStockDetailResponse_nyse_doesNotAddSuffixToLogoUrl() {
        // given
        Stock stock = Stock.of("JPM", "JPMORGAN CHASE & CO", MarketType.NYSE, ListingStatus.LISTED, "610", "제이피모간체이스");

        // when
        StockDetailResponse response = StockMapper.toStockDetailResponse(stock);

        // then
        assertThat(response.logoUrl())
            .isEqualTo("https://ssl.pstatic.net/imgstock/fn/real/logo/png/stock/StockJPM.png");
    }

    @Test
    @DisplayName("[한글명이 아직 백필되지 않은 해외 종목은 영문명을 그대로 노출한다]")
    void toStockDetailResponse_overseasWithoutKoreanName_fallsBackToStockName() {
        // given
        Stock stock = Stock.of("TSLA", "TESLA INC", MarketType.NASDAQ, ListingStatus.LISTED, "720");

        // when
        StockDetailResponse response = StockMapper.toStockDetailResponse(stock);

        // then
        assertThat(response.stockName()).isEqualTo("TESLA INC");
    }
}
