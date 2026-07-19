package com.quantlab.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlab.price.dto.StockTradingValue;
import com.quantlab.price.repository.DailyPriceRepository;
import com.quantlab.price.service.DailyPriceService;
import com.quantlab.stock.domain.ListingStatus;
import com.quantlab.stock.domain.MarketType;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.service.StockMasterService;
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
class DomesticUniverseSelectionServiceTest {

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private DailyPriceService dailyPriceService;

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @InjectMocks
    private DomesticUniverseSelectionService domesticUniverseSelectionService;

    @Test
    @DisplayName("[REIT는 후보에서 제외하고, 이름에 리츠가 들어가도 REIT가 아닌 예외 종목은 포함한다]")
    void selectAndBackfillUniverse_excludesReitButKeepsFalsePositives() {
        // given
        Stock reit = stock("450140", "삼성FN리츠");
        Stock notReitButContainsName = stock("138040", "메리츠금융지주");
        Stock normalStock = stock("005930", "삼성전자");
        given(stockMasterService.getAllListedStocks())
            .willReturn(List.of(reit, notReitButContainsName, normalStock));
        given(dailyPriceRepository.findTopByTradingValue(any(), eq(500)))
            .willReturn(List.of(new StockTradingValue("005930", 1_000_000L)));

        // when
        List<String> selected = domesticUniverseSelectionService.selectAndBackfillUniverse();

        // then: 1차 스캔은 REIT(삼성FN리츠)만 제외한 2종목에 대해서만 호출
        verify(dailyPriceService, times(1)).backfillHistoryIfNeeded("138040", 60);
        verify(dailyPriceService, times(1)).backfillHistoryIfNeeded("005930", 60);
        verify(dailyPriceService, never()).backfillHistoryIfNeeded(eq("450140"), anyInt());
        assertThat(selected).containsExactly("005930");
    }

    @Test
    @DisplayName("[선정된 종목만 400일 목표로 2차 백필한다]")
    void selectAndBackfillUniverse_deepensOnlySelected() {
        // given
        given(stockMasterService.getAllListedStocks())
            .willReturn(List.of(stock("005930", "삼성전자"), stock("000660", "SK하이닉스")));
        given(dailyPriceRepository.findTopByTradingValue(any(), eq(500)))
            .willReturn(List.of(new StockTradingValue("005930", 1_000_000L)));

        // when
        domesticUniverseSelectionService.selectAndBackfillUniverse();

        // then
        verify(dailyPriceService, times(1)).backfillHistoryIfNeeded("005930", 400);
        verify(dailyPriceService, never()).backfillHistoryIfNeeded(eq("000660"), eq(400));
    }

    @Test
    @DisplayName("[개별 종목 백필이 실패해도 나머지 종목 처리를 계속한다]")
    void selectAndBackfillUniverse_continuesOnIndividualFailure() {
        // given
        given(stockMasterService.getAllListedStocks())
            .willReturn(List.of(stock("005930", "삼성전자"), stock("000660", "SK하이닉스")));
        doThrowOnBackfill("005930", 60);
        given(dailyPriceRepository.findTopByTradingValue(any(), eq(500))).willReturn(List.of());

        // when & then: 예외가 전파되지 않고 나머지 종목까지 처리됨
        domesticUniverseSelectionService.selectAndBackfillUniverse();
        verify(dailyPriceService, times(1)).backfillHistoryIfNeeded("000660", 60);
    }

    private void doThrowOnBackfill(String stockCode, int targetDays) {
        org.mockito.Mockito.doThrow(new RuntimeException("백필 실패"))
            .when(dailyPriceService).backfillHistoryIfNeeded(stockCode, targetDays);
    }

    private Stock stock(String code, String name) {
        return Stock.of(code, name, MarketType.KOSPI, ListingStatus.LISTED, "기타");
    }
}
