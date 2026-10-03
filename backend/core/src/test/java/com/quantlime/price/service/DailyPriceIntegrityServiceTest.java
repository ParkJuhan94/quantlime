package com.quantlime.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.dto.PriceJumpReport;
import com.quantlime.price.implement.DailyPriceReader;
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
class DailyPriceIntegrityServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private DailyPriceReader dailyPriceReader;

    @Mock
    private DomesticDailyPriceService domesticDailyPriceService;

    @InjectMocks
    private DailyPriceIntegrityService service;

    private DomesticDailyPrice price(String code, LocalDate date, long close) {
        return DomesticDailyPrice.of(code, date, close, close, close, close, 1000L);
    }

    private void givenStocks(Stock... stocks) {
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(stocks));
    }

    @Test
    @DisplayName("[스캔은 국내 종목만 대상으로, DB에 저장된 시계열에서 이상 점프를 찾는다(입력 순서와 무관하게 날짜순 정렬)]")
    void scanDomesticJumps_findsJumpAmongDomesticStocksOnly() {
        // given: 국내 1종목(삼성전자) + 해외 1종목(조회 대상에서 제외돼야 함)
        givenStocks(StockFixture.createStock("005930", "삼성전자"),
            StockFixture.createOverseasStock("AAPL", "Apple"));
        // 리포지토리는 tradeDate 내림차순으로 돌려준다 - 서비스가 오름차순으로 다시 정렬해야 한다
        given(dailyPriceReader.findDomesticBetweenForCodes(
            anyList(), any(), any())).willReturn(List.of(
                price("005930", FROM.plusDays(2), 200L),
                price("005930", FROM.plusDays(1), 1000L),
                price("005930", FROM, 1010L)));

        // when
        List<PriceJumpReport> jumps = service.scanDomesticJumps(FROM);

        // then
        assertThat(jumps).hasSize(1);
        assertThat(jumps.get(0).stockCode()).isEqualTo("005930");
        assertThat(jumps.get(0).tradeDate()).isEqualTo(FROM.plusDays(2));
        verify(dailyPriceReader).findDomesticBetweenForCodes(
            List.of("005930"), FROM, LocalDate.now());
    }

    @Test
    @DisplayName("[스캔 결과가 없으면 빈 리스트]")
    void scanDomesticJumps_noJumps_returnsEmpty() {
        givenStocks(StockFixture.createStock("005930", "삼성전자"));
        given(dailyPriceReader.findDomesticBetweenForCodes(
            anyList(), any(), any())).willReturn(List.of(
                price("005930", FROM.plusDays(1), 1010L), price("005930", FROM, 1000L)));

        assertThat(service.scanDomesticJumps(FROM)).isEmpty();
    }

    @Test
    @DisplayName("[dryRun=true면 스캔 결과만 반환하고 재백필(외부 API)은 호출하지 않는다]")
    void repairDomesticAdjusted_dryRun_doesNotRebackfill() {
        givenStocks(StockFixture.createStock("005930", "삼성전자"));
        given(dailyPriceReader.findDomesticBetweenForCodes(
            anyList(), any(), any())).willReturn(List.of(
                price("005930", FROM.plusDays(1), 200L), price("005930", FROM, 1000L)));

        List<PriceJumpReport> jumps = service.repairDomesticAdjusted(FROM, true);

        assertThat(jumps).hasSize(1);
        verify(domesticDailyPriceService, never()).rebackfillAdjustedHistory(any());
    }

    @Test
    @DisplayName("[실복구는 점프가 여러 번 발견된 종목도 종목당 1회만 재백필하고, 한 종목 실패가 다음 종목을 막지 않는다]")
    void repairDomesticAdjusted_rebackfillsEachAffectedStockOnce_isolatingFailures() {
        // given: A는 점프 2회, B는 점프 1회
        givenStocks(StockFixture.createStock("A00001", "A"), StockFixture.createStock("B00002", "B"));
        given(dailyPriceReader.findDomesticBetweenForCodes(
            anyList(), any(), any())).willReturn(List.of(
                price("A00001", FROM, 1000L), price("A00001", FROM.plusDays(1), 200L),
                price("A00001", FROM.plusDays(2), 1000L),
                price("B00002", FROM, 1000L), price("B00002", FROM.plusDays(1), 200L)));
        // 다른 인자 호출이 strict stubs의 인자 불일치 예외로 오인되지 않게 lenient로 건다
        lenient().doThrow(new RuntimeException("toss down")).when(domesticDailyPriceService)
            .rebackfillAdjustedHistory("A00001");

        // when
        List<PriceJumpReport> jumps = service.repairDomesticAdjusted(FROM, false);

        // then
        assertThat(jumps).hasSize(3);
        verify(domesticDailyPriceService).rebackfillAdjustedHistory("A00001");
        verify(domesticDailyPriceService).rebackfillAdjustedHistory("B00002");
    }

    @Test
    @DisplayName("[스캔은 종목이 100개를 넘으면 100개씩 나눠 조회한다]")
    void scanDomesticJumps_partitionsStocksBy100() {
        // given: 국내 종목 250개
        Stock[] stocks = new Stock[250];
        for (int i = 0; i < stocks.length; i++) {
            stocks[i] = StockFixture.createStock(String.format("%06d", i), "S" + i);
        }
        givenStocks(stocks);
        given(dailyPriceReader.findDomesticBetweenForCodes(
            anyList(), any(), any())).willReturn(List.of());

        // when
        service.scanDomesticJumps(FROM);

        // then: 100 + 100 + 50 = 3번 조회
        verify(dailyPriceReader, org.mockito.Mockito.times(3))
            .findDomesticBetweenForCodes(anyList(), any(), any());
    }
}
