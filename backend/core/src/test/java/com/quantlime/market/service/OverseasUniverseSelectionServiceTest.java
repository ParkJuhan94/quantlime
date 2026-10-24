package com.quantlime.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.price.implement.DailyPriceReader;
import com.quantlime.price.service.OverseasDailyPriceBackfillService;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OverseasUniverseSelectionServiceTest {

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;

    @Mock
    private DailyPriceReader dailyPriceReader;

    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private OverseasUniverseSelectionService overseasUniverseSelectionService;

    @Test
    @DisplayName("[국내 종목은 후보에서 제외하고 NASDAQ/NYSE만 스캔한다]")
    void selectAndBackfillUniverse_excludesDomesticStocks() {
        // given
        Stock domestic = stock("005930", "삼성전자", MarketType.KOSPI);
        Stock nasdaq = stock("AAPL", "APPLE INC", MarketType.NASDAQ);
        Stock nyse = stock("AA", "ALCOA CORPORATION", MarketType.NYSE);
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(domestic, nasdaq, nyse));
        given(dailyPriceReader.findOverseasCodesByTradingValueDesc(any())).willReturn(List.of("AAPL"));

        // when
        List<String> selected = overseasUniverseSelectionService.selectAndBackfillUniverse();

        // then
        verify(overseasDailyPriceBackfillService, times(1))
            .backfillHistoryIfNeeded("AAPL", 60);
        verify(overseasDailyPriceBackfillService, times(1))
            .backfillHistoryIfNeeded("AA", 60);
        verify(overseasDailyPriceBackfillService, times(0))
            .backfillHistoryIfNeeded(eq("005930"), org.mockito.ArgumentMatchers.anyInt());
        assertThat(selected).containsExactly("AAPL");
    }

    @Test
    @DisplayName("[벤치마크 지수가 없는 AMEX 종목은 거래대금 순위가 높아도 후보·백필·유니버스에서 모두 제외한다]")
    void selectAndBackfillUniverse_excludesMarketsWithoutBenchmark() {
        // given: 거래대금 순위에서 AMEX 종목이 NASDAQ 종목보다 앞선다
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(
            stock("AMXA", "AMEX LISTED CO", MarketType.AMEX),
            stock("AAPL", "APPLE INC", MarketType.NASDAQ)
        ));
        given(dailyPriceReader.findOverseasCodesByTradingValueDesc(any())).willReturn(List.of("AMXA", "AAPL"));

        // when
        List<String> selected = overseasUniverseSelectionService.selectAndBackfillUniverse();

        // then: AMEX는 1차 스캔과 2차 백필 어디에도 호출되지 않고, 반환되는 유니버스에도 없다
        assertThat(selected).containsExactly("AAPL");
        verify(overseasDailyPriceBackfillService, times(0))
            .backfillHistoryIfNeeded(eq("AMXA"), org.mockito.ArgumentMatchers.anyInt());
        verify(overseasDailyPriceBackfillService, times(1)).backfillHistoryIfNeeded("AAPL", 60);
        verify(overseasDailyPriceBackfillService, times(1)).backfillHistoryIfNeeded("AAPL", 520);
    }

    @Test
    @DisplayName("[상장폐지돼 종목마스터에 없는 코드는 거래대금 순위에 남아 있어도 유니버스에서 제외한다]")
    void selectAndBackfillUniverse_excludesCodesNotListed() {
        // given
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(stock("AAPL", "APPLE INC", MarketType.NASDAQ)));
        given(dailyPriceReader.findOverseasCodesByTradingValueDesc(any())).willReturn(List.of("DELISTED", "AAPL"));

        // when
        List<String> selected = overseasUniverseSelectionService.selectAndBackfillUniverse();

        // then
        assertThat(selected).containsExactly("AAPL");
    }

    @Test
    @DisplayName("[업종코드가 630(부동산/REIT)인 종목은 후보에서 제외한다]")
    void selectAndBackfillUniverse_excludesRealEstateSector() {
        // given
        Stock reit = stock("O", "REALTY INCOME CORP", MarketType.NYSE, "630");
        Stock normalStock = stock("AAPL", "APPLE INC", MarketType.NASDAQ, "720");
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(reit, normalStock));
        given(dailyPriceReader.findOverseasCodesByTradingValueDesc(any())).willReturn(List.of("AAPL"));

        // when
        List<String> selected = overseasUniverseSelectionService.selectAndBackfillUniverse();

        // then: 1차 스캔은 REIT(O)만 제외한 1종목에 대해서만 호출
        verify(overseasDailyPriceBackfillService, times(1))
            .backfillHistoryIfNeeded("AAPL", 60);
        verify(overseasDailyPriceBackfillService, org.mockito.Mockito.never())
            .backfillHistoryIfNeeded(eq("O"), org.mockito.ArgumentMatchers.anyInt());
        assertThat(selected).containsExactly("AAPL");
    }

    @Test
    @DisplayName("[선정된 종목만 520일 목표로 2차 백필한다]")
    void selectAndBackfillUniverse_deepensOnlySelected() {
        // given
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(
            stock("AAPL", "APPLE INC", MarketType.NASDAQ),
            stock("MSFT", "MICROSOFT CORP", MarketType.NASDAQ)
        ));
        given(dailyPriceReader.findOverseasCodesByTradingValueDesc(any())).willReturn(List.of("AAPL"));

        // when
        overseasUniverseSelectionService.selectAndBackfillUniverse();

        // then
        verify(overseasDailyPriceBackfillService, times(1))
            .backfillHistoryIfNeeded("AAPL", 520);
        verify(overseasDailyPriceBackfillService, times(0))
            .backfillHistoryIfNeeded(eq("MSFT"), eq(520));
    }

    private Stock stock(String code, String name, MarketType marketType) {
        return stock(code, name, marketType, null);
    }

    private Stock stock(String code, String name, MarketType marketType, String sector) {
        return Stock.of(code, name, marketType, ListingStatus.LISTED, sector);
    }
}
