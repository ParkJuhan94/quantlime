package com.quantlime.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.LocalDate;
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
class DailyPriceResettlementServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private DomesticDailyPriceService domesticDailyPriceService;

    @Mock
    private OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;

    @InjectMocks
    private DailyPriceResettlementService service;

    @Test
    @DisplayName("[국내 재확정은 국내 종목만, 가격 미지원 종목은 제외하고 from부터 재백필한다]")
    void resettleDomestic_skipsOverseasAndPriceUnsupported() {
        // given
        Stock unsupported = StockFixture.createStock("999999", "미지원");
        unsupported.markPriceUnsupported();
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(
            StockFixture.createStock("005930", "삼성전자"),
            unsupported,
            StockFixture.createOverseasStock("AAPL", "Apple")));

        // when
        int processed = service.resettleDomestic(FROM);

        // then
        assertThat(processed).isEqualTo(1);
        verify(domesticDailyPriceService).rebackfillAdjustedHistory("005930", FROM);
        verify(domesticDailyPriceService, never()).rebackfillAdjustedHistory("999999", FROM);
        verify(overseasDailyPriceBackfillService, never()).rebackfillAdjustedHistory("AAPL", FROM);
    }

    @Test
    @DisplayName("[해외 재확정은 해외 종목만 대상으로 한다]")
    void resettleOverseas_onlyOverseas() {
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(
            StockFixture.createStock("005930", "삼성전자"),
            StockFixture.createOverseasStock("AAPL", "Apple")));

        int processed = service.resettleOverseas(FROM);

        assertThat(processed).isEqualTo(1);
        verify(overseasDailyPriceBackfillService).rebackfillAdjustedHistory("AAPL", FROM);
        verify(domesticDailyPriceService, never()).rebackfillAdjustedHistory("005930", FROM);
    }

    @Test
    @DisplayName("[한 종목 재백필이 실패해도 나머지는 계속 처리하고, 처리 건수에는 실패분이 빠진다]")
    void resettle_failureIsIsolatedPerStock() {
        // given
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(
            StockFixture.createStock("A00001", "A"),
            StockFixture.createStock("B00002", "B"),
            StockFixture.createStock("C00003", "C")));
        // 다른 인자 호출이 strict stubs의 인자 불일치 예외로 오인되지 않게 lenient로 건다
        lenient().doThrow(new RuntimeException("toss 429")).when(domesticDailyPriceService)
            .rebackfillAdjustedHistory("B00002", FROM);

        // when
        int processed = service.resettleDomestic(FROM);

        // then
        assertThat(processed).isEqualTo(2);
        verify(domesticDailyPriceService).rebackfillAdjustedHistory("C00003", FROM);
    }

    @Test
    @DisplayName("[대상 종목이 없으면 0을 반환한다]")
    void resettle_noStocks_returnsZero() {
        given(stockMasterService.getAllListedStocks()).willReturn(List.of());

        assertThat(service.resettleDomestic(FROM)).isZero();
    }
}
