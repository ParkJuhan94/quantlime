package com.quantlime.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.price.dto.OverseasStockTradingValue;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.price.service.OverseasDailyPriceBackfillService;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OverseasUniverseSelectionServiceTest {

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;

    @Mock
    private OverseasDailyPriceRepository overseasDailyPriceRepository;

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
        given(overseasDailyPriceRepository.findTopByTradingValue(any(), eq(500)))
            .willReturn(List.of(new OverseasStockTradingValue("AAPL", 1_000_000.0)));

        // when
        List<String> selected = overseasUniverseSelectionService.selectAndBackfillUniverse();

        // then
        verify(overseasDailyPriceBackfillService, times(1))
            .backfillHistoryIfNeeded("AAPL", "NAS", 60);
        verify(overseasDailyPriceBackfillService, times(1))
            .backfillHistoryIfNeeded("AA", "NYS", 60);
        verify(overseasDailyPriceBackfillService, times(0))
            .backfillHistoryIfNeeded(eq("005930"), any(), org.mockito.ArgumentMatchers.anyInt());
        assertThat(selected).containsExactly("AAPL");
    }

    @Test
    @DisplayName("[선정된 종목만 400일 목표로 2차 백필한다]")
    void selectAndBackfillUniverse_deepensOnlySelected() {
        // given
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(
            stock("AAPL", "APPLE INC", MarketType.NASDAQ),
            stock("MSFT", "MICROSOFT CORP", MarketType.NASDAQ)
        ));
        given(overseasDailyPriceRepository.findTopByTradingValue(any(), eq(500)))
            .willReturn(List.of(new OverseasStockTradingValue("AAPL", 1_000_000.0)));

        // when
        overseasUniverseSelectionService.selectAndBackfillUniverse();

        // then
        verify(overseasDailyPriceBackfillService, times(1))
            .backfillHistoryIfNeeded("AAPL", "NAS", 400);
        verify(overseasDailyPriceBackfillService, times(0))
            .backfillHistoryIfNeeded(eq("MSFT"), any(), eq(400));
    }

    private Stock stock(String code, String name, MarketType marketType) {
        return Stock.of(code, name, marketType, ListingStatus.LISTED, null);
    }
}
