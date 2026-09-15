package com.quantlime.stock.service;

import com.quantlime.infra.dart.DartApiClient;
import com.quantlime.infra.dart.dto.DartCorpInfo;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossStockInfoResponse;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.dto.StockMasterSyncResult;
import com.quantlime.stock.repository.StockRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DomesticStockMasterSyncServiceTest {

    @Mock
    private DartApiClient dartApiClient;

    @Mock
    private TossApiClient tossApiClient;

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private DomesticStockMasterSyncService domesticStockMasterSyncService;

    @Test
    @DisplayName("[DART 목록에는 있는데 DB에 없는 종목은 Toss로 시장구분을 조회해 신규상장으로 등록한다]")
    void syncStockMaster_newCode_registersAsListed() {
        // given
        given(dartApiClient.fetchCorpList()).willReturn(List.of(
            new DartCorpInfo("00126380", "삼성전자", "005930"),
            new DartCorpInfo("00164779", "SK하이닉스", "000660")));
        given(stockRepository.findAll()).willReturn(
            List.of(StockFixture.createStock("005930", "삼성전자")));
        given(tossApiClient.getStockInfo(any())).willReturn(new TossStockInfoResponse(
            List.of(new TossStockInfoResponse.TossStockInfo("000660", "KOSPI"))));

        // when
        StockMasterSyncResult result = domesticStockMasterSyncService.syncStockMaster();

        // then
        ArgumentCaptor<Stock> savedCaptor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStockCode()).isEqualTo("000660");
        assertThat(savedCaptor.getValue().getMarketType()).isEqualTo(MarketType.KOSPI);
        assertThat(savedCaptor.getValue().getListingStatus()).isEqualTo(ListingStatus.LISTED);
        assertThat(result.newlyListedCount()).isEqualTo(1);
        assertThat(result.delistedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("[Toss가 KOSPI/KOSDAQ로 분류하지 못하는(코넥스 등) 신규 종목은 등록을 스킵한다]")
    void syncStockMaster_unresolvableMarket_skipsRegistration() {
        // given
        given(dartApiClient.fetchCorpList()).willReturn(List.of(
            new DartCorpInfo("00999999", "코넥스종목", "900001")));
        given(stockRepository.findAll()).willReturn(List.of());
        given(tossApiClient.getStockInfo(any())).willReturn(new TossStockInfoResponse(List.of()));

        // when
        StockMasterSyncResult result = domesticStockMasterSyncService.syncStockMaster();

        // then
        verify(stockRepository, never()).save(any());
        assertThat(result.newlyListedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("[DB에는 상장 상태인데 DART 목록에서 사라진 종목은 상장폐지 처리한다]")
    void syncStockMaster_missingFromDartList_marksDelisted() {
        // given
        Stock delistedCandidate = StockFixture.createStock("005930", "삼성전자");
        given(dartApiClient.fetchCorpList()).willReturn(List.of());
        given(stockRepository.findAll()).willReturn(List.of(delistedCandidate));

        // when
        StockMasterSyncResult result = domesticStockMasterSyncService.syncStockMaster();

        // then
        assertThat(delistedCandidate.getListingStatus()).isEqualTo(ListingStatus.DELISTED);
        assertThat(result.delistedCount()).isEqualTo(1);
        assertThat(result.newlyListedCount()).isEqualTo(0);
        verify(stockRepository, never()).save(eq(delistedCandidate));
    }

    @Test
    @DisplayName("[해외종목은 DART 목록에 없어도 상장폐지 처리하지 않는다]")
    void syncStockMaster_overseasStock_neverMarkedDelisted() {
        // given: DART는 국내 전용 소스라 해외종목(AAPL)은 latest 맵에 절대 없다 -
        // 시장 구분 없이 비교하면 이 종목까지 상장폐지로 오판된다(KIND 시절 실제
        // 재현된 버그와 동일한 함정)
        Stock overseasStock = Stock.of("AAPL", "APPLE INC", MarketType.NASDAQ, ListingStatus.LISTED, null);
        given(dartApiClient.fetchCorpList()).willReturn(List.of());
        given(stockRepository.findAll()).willReturn(List.of(overseasStock));

        // when
        StockMasterSyncResult result = domesticStockMasterSyncService.syncStockMaster();

        // then
        assertThat(overseasStock.getListingStatus()).isEqualTo(ListingStatus.LISTED);
        assertThat(result.delistedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("[DART 목록과 DB가 동일하면 아무 것도 변경하지 않는다]")
    void syncStockMaster_noDifference_doesNothing() {
        // given
        Stock existing = StockFixture.createStock("005930", "삼성전자");
        given(dartApiClient.fetchCorpList()).willReturn(List.of(
            new DartCorpInfo("00126380", "삼성전자", "005930")));
        given(stockRepository.findAll()).willReturn(List.of(existing));

        // when
        StockMasterSyncResult result = domesticStockMasterSyncService.syncStockMaster();

        // then
        assertThat(result.newlyListedCount()).isEqualTo(0);
        assertThat(result.delistedCount()).isEqualTo(0);
        assertThat(existing.getListingStatus()).isEqualTo(ListingStatus.LISTED);
        verify(stockRepository, never()).save(eq(existing));
    }
}
