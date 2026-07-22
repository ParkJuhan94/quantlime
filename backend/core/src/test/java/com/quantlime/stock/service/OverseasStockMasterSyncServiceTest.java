package com.quantlime.stock.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.kis.KisOverseasStockMasterClient;
import com.quantlime.infra.kis.dto.KisOverseasStockMasterEntry;
import com.quantlime.stock.domain.MarketType;
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
class OverseasStockMasterSyncServiceTest {

    @Mock
    private KisOverseasStockMasterClient kisOverseasStockMasterClient;

    @Mock
    private StockMasterService stockMasterService;

    @InjectMocks
    private OverseasStockMasterSyncService overseasStockMasterSyncService;

    @Test
    @DisplayName("[ETF(종목구분 3)는 등록하지 않고 주식(종목구분 2)만 등록한다]")
    void syncMarket_registersOnlyStocks() {
        // given
        given(kisOverseasStockMasterClient.fetchStockMaster("nys")).willReturn(List.of(
            new KisOverseasStockMasterEntry("AA", "ALCOA CORPORATION", "2"),
            new KisOverseasStockMasterEntry("SPY", "SPDR S&P 500 ETF", "3")
        ));

        // when
        overseasStockMasterSyncService.syncMarket(MarketType.NYSE);

        // then: ETF까지 등록됐다면 2회 호출됐을 것 - 실제로는 주식 1건만
        verify(stockMasterService, times(1))
            .registerStock("AA", "ALCOA CORPORATION", MarketType.NYSE, null);
        verify(stockMasterService, times(1))
            .registerStock(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("[6자를 초과하는 심볼은 등록하지 않고 건너뛴다]")
    void syncMarket_skipsSymbolsLongerThanSix() {
        // given
        given(kisOverseasStockMasterClient.fetchStockMaster("nys")).willReturn(List.of(
            new KisOverseasStockMasterEntry("XFLH/UN", "SOME SPAC UNIT", "2")
        ));

        // when
        overseasStockMasterSyncService.syncMarket(MarketType.NYSE);

        // then
        verify(stockMasterService, never())
            .registerStock(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("[syncAll은 NASDAQ/NYSE 둘 다 동기화한다]")
    void syncAll_syncsBothMarkets() {
        // given
        given(kisOverseasStockMasterClient.fetchStockMaster("nas")).willReturn(List.of());
        given(kisOverseasStockMasterClient.fetchStockMaster("nys")).willReturn(List.of());

        // when
        overseasStockMasterSyncService.syncAll();

        // then
        verify(kisOverseasStockMasterClient).fetchStockMaster("nas");
        verify(kisOverseasStockMasterClient).fetchStockMaster("nys");
    }
}
