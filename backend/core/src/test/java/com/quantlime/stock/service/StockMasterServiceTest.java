package com.quantlime.stock.service;

import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockMasterServiceTest {

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private StockMasterService stockMasterService;

    @Test
    @DisplayName("[신규 종목은 한글명을 포함해 그대로 등록한다]")
    void registerStock_newStock_savesWithKoreanName() {
        // given
        given(stockRepository.findByStockCode("AAPL")).willReturn(Optional.empty());
        given(stockRepository.save(org.mockito.ArgumentMatchers.any(Stock.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        Stock result = stockMasterService.registerStock(
            "AAPL", "APPLE INC", MarketType.NASDAQ, "720", "애플");

        // then
        ArgumentCaptor<Stock> captor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(captor.capture());
        assertThat(captor.getValue().getKoreanName()).isEqualTo("애플");
        assertThat(captor.getValue().getDisplayName()).isEqualTo("애플");
        assertThat(result.getDisplayName()).isEqualTo("애플");
    }

    @Test
    @DisplayName("[이미 등록된 종목은 한글명만 최신 값으로 백필하고 재저장하지 않는다]")
    void registerStock_existingStock_backfillsKoreanNameOnly() {
        // given: 이전 동기화에서 한글명 없이 등록됐던 종목
        Stock existing = Stock.of("AAPL", "APPLE INC", MarketType.NASDAQ, ListingStatus.LISTED, "720");
        given(stockRepository.findByStockCode("AAPL")).willReturn(Optional.of(existing));

        // when
        Stock result = stockMasterService.registerStock(
            "AAPL", "APPLE INC", MarketType.NASDAQ, "720", "애플");

        // then
        assertThat(result).isSameAs(existing);
        assertThat(result.getKoreanName()).isEqualTo("애플");
        verify(stockRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
