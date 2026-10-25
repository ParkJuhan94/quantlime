package com.quantlime.tradingflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import com.quantlime.tradingflow.domain.InstitutionBreakdown;
import com.quantlime.tradingflow.domain.InvestorVolume;
import com.quantlime.tradingflow.domain.StockInvestorTrading;
import com.quantlime.tradingflow.dto.response.StockInvestorTradingResponse;
import com.quantlime.tradingflow.implement.StockInvestorTradingReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class StockInvestorTradingServiceTest {

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private StockInvestorTradingReader reader;

    @InjectMocks
    private StockInvestorTradingService service;

    private StockInvestorTrading row(String code, LocalDate date, boolean confirmed) {
        InvestorVolume individual = confirmed ? InvestorVolume.of(10, 30, -20) : null;
        InstitutionBreakdown breakdown = confirmed
            ? InstitutionBreakdown.builder().financialInvestmentNetBuyVolume(1L).insuranceNetBuyVolume(2L)
                .trustNetBuyVolume(3L).privateEquityFundNetBuyVolume(4L).bankNetBuyVolume(5L)
                .otherFinancialInstitutionNetBuyVolume(6L).pensionFundNetBuyVolume(7L).build()
            : null;
        return StockInvestorTrading.of(code, date, LocalDateTime.of(2026, 10, 23, 18, 0), individual,
            InvestorVolume.of(50, 40, 10), InvestorVolume.of(30, 20, 10), breakdown, null, null, 0.5089);
    }

    @Test
    @DisplayName("[최신순으로 읽은 기록을 오름차순으로 뒤집어 순매수만 담아 돌려준다]")
    void getRecent_reversesToAscendingAndMapsNetBuy() {
        Stock stock = StockFixture.createStock("005930", "삼성전자");
        given(stockMasterService.getStockByCode("005930")).willReturn(stock);
        given(reader.findLatest("005930", 2)).willReturn(List.of(
            row("005930", LocalDate.of(2026, 10, 23), false),
            row("005930", LocalDate.of(2026, 10, 22), true)));

        List<StockInvestorTradingResponse> result = service.getRecent("005930", 2);

        assertThat(result).extracting(StockInvestorTradingResponse::tradeDate)
            .containsExactly(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23));
        assertThat(result.get(0).individualNetBuyVolume()).isEqualTo(-20L);
        assertThat(result.get(0).confirmed()).isTrue();
        assertThat(result.get(0).institutionBreakdown().pensionFundNetBuyVolume()).isEqualTo(7L);
        assertThat(result.get(0).institutionBreakdown().financialInvestmentNetBuyVolume()).isEqualTo(1L);
        assertThat(result.get(1).institutionBreakdown()).isNull();
        assertThat(result.get(1).individualNetBuyVolume()).isNull();
        assertThat(result.get(1).confirmed()).isFalse();
        assertThat(result.get(1).foreignerNetBuyVolume()).isEqualTo(10L);
        assertThat(result.get(1).foreignerHoldingRate()).isEqualTo(0.5089);
    }

    @Test
    @DisplayName("[해외 종목은 토스가 제공하지 않아 저장소를 읽지 않고 빈 목록을 돌려준다]")
    void getRecent_overseasStock_returnsEmptyWithoutReading() {
        Stock overseas = StockFixture.createOverseasStock("AAPL", "Apple");
        given(stockMasterService.getStockByCode("AAPL")).willReturn(overseas);

        List<StockInvestorTradingResponse> result = service.getRecent("AAPL", 20);

        assertThat(result).isEmpty();
        verifyNoInteractions(reader);
    }
}
