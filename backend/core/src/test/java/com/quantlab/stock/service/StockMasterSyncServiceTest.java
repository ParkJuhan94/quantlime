package com.quantlab.stock.service;

import com.quantlab.infra.kind.KindApiClient;
import com.quantlab.infra.kind.dto.KindStockInfo;
import com.quantlab.stock.StockFixture;
import com.quantlab.stock.domain.ListingStatus;
import com.quantlab.stock.domain.MarketType;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.dto.StockMasterSyncResult;
import com.quantlab.stock.repository.StockRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockMasterSyncServiceTest {

    @Mock
    private KindApiClient kindApiClient;

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private StockMasterSyncService stockMasterSyncService;

    @Test
    @DisplayName("[KIND 목록에는 있는데 DB에 없는 종목은 신규상장으로 등록한다]")
    void syncStockMaster_newCode_registersAsListed() {
        // given
        given(kindApiClient.fetchCorpList(MarketType.KOSPI)).willReturn(List.of(
            new KindStockInfo("005930", "삼성전자", MarketType.KOSPI, "전기전자"),
            new KindStockInfo("000660", "SK하이닉스", MarketType.KOSPI, "전기전자")));
        given(kindApiClient.fetchCorpList(MarketType.KOSDAQ)).willReturn(List.of());
        given(kindApiClient.fetchCorpList(MarketType.KONEX)).willReturn(List.of());
        given(stockRepository.findAll()).willReturn(
            List.of(StockFixture.createStock("005930", "삼성전자")));

        // when
        StockMasterSyncResult result = stockMasterSyncService.syncStockMaster();

        // then
        ArgumentCaptor<Stock> savedCaptor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStockCode()).isEqualTo("000660");
        assertThat(savedCaptor.getValue().getListingStatus()).isEqualTo(ListingStatus.LISTED);
        assertThat(result.newlyListedCount()).isEqualTo(1);
        assertThat(result.delistedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("[DB에는 상장 상태인데 KIND 목록에서 사라진 종목은 상장폐지 처리한다]")
    void syncStockMaster_missingFromKindList_marksDelisted() {
        // given
        Stock delistedCandidate = StockFixture.createStock("005930", "삼성전자");
        given(kindApiClient.fetchCorpList(MarketType.KOSPI)).willReturn(List.of());
        given(kindApiClient.fetchCorpList(MarketType.KOSDAQ)).willReturn(List.of());
        given(kindApiClient.fetchCorpList(MarketType.KONEX)).willReturn(List.of());
        given(stockRepository.findAll()).willReturn(List.of(delistedCandidate));

        // when
        StockMasterSyncResult result = stockMasterSyncService.syncStockMaster();

        // then
        assertThat(delistedCandidate.getListingStatus()).isEqualTo(ListingStatus.DELISTED);
        assertThat(result.delistedCount()).isEqualTo(1);
        assertThat(result.newlyListedCount()).isEqualTo(0);
        verify(stockRepository, never()).save(eq(delistedCandidate));
    }

    @Test
    @DisplayName("[KIND 목록과 DB가 동일하면 아무 것도 변경하지 않는다]")
    void syncStockMaster_noDifference_doesNothing() {
        // given
        Stock existing = StockFixture.createStock("005930", "삼성전자");
        given(kindApiClient.fetchCorpList(MarketType.KOSPI)).willReturn(List.of(
            new KindStockInfo("005930", "삼성전자", MarketType.KOSPI, "전기전자")));
        given(kindApiClient.fetchCorpList(MarketType.KOSDAQ)).willReturn(List.of());
        given(kindApiClient.fetchCorpList(MarketType.KONEX)).willReturn(List.of());
        given(stockRepository.findAll()).willReturn(List.of(existing));

        // when
        StockMasterSyncResult result = stockMasterSyncService.syncStockMaster();

        // then
        assertThat(result.newlyListedCount()).isEqualTo(0);
        assertThat(result.delistedCount()).isEqualTo(0);
        assertThat(existing.getListingStatus()).isEqualTo(ListingStatus.LISTED);
        verify(stockRepository, never()).save(eq(existing));
    }
}
