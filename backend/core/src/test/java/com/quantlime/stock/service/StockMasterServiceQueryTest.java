package com.quantlime.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.stock.cache.StockSearchCache;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.implement.StockAppender;
import com.quantlime.stock.implement.StockReader;
import com.quantlime.stock.repository.StockRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

/** 조회·가격 미지원 표시·일괄 등록·순서 보존 경로. 등록(registerStock)은 {@link StockMasterServiceTest}가 맡는다. */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockMasterServiceQueryTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockSearchCache stockSearchCache;

    private StockMasterService service;

    @BeforeEach
    void setUp() {
        service = new StockMasterService(
            new StockReader(stockRepository), new StockAppender(stockRepository), stockSearchCache);
    }

    private Stock stock(String code) {
        return Stock.of(code, code + " NAME", MarketType.KOSPI, ListingStatus.LISTED, "01");
    }

    @Test
    @DisplayName("[상장 종목 조회는 LISTED 상태로 위임한다]")
    void getAllListedStocks_queriesListedStatus() {
        given(stockRepository.findByListingStatus(ListingStatus.LISTED)).willReturn(List.of(stock("005930")));

        assertThat(service.getAllListedStocks()).extracting(Stock::getStockCode).containsExactly("005930");
    }

    @Test
    @DisplayName("[종목코드 단건 조회 - 없으면 NotFoundException]")
    void getStockByCode_missing_throws() {
        given(stockRepository.findByStockCode("005930")).willReturn(Optional.of(stock("005930")));
        given(stockRepository.findByStockCode("999999")).willReturn(Optional.empty());

        assertThat(service.getStockByCode("005930").getStockCode()).isEqualTo("005930");
        assertThatThrownBy(() -> service.getStockByCode("999999")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("[가격 미지원 표시 - 종목이 있으면 표시하고, 없으면 조용히 넘어간다]")
    void markPriceUnsupported_marksExistingAndIgnoresMissing() {
        Stock existing = stock("005930");
        given(stockRepository.findByStockCode("005930")).willReturn(Optional.of(existing));
        given(stockRepository.findByStockCode("999999")).willReturn(Optional.empty());

        service.markPriceUnsupported("005930");
        service.markPriceUnsupported("999999");

        assertThat(existing.isPriceUnsupported()).isTrue();
    }

    @Test
    @DisplayName("[일괄 등록은 saveAll로 한 번에 저장한다]")
    void bulkRegisterStocks_savesAll() {
        List<Stock> stocks = List.of(stock("005930"), stock("000660"));

        service.bulkRegisterStocks(stocks);

        verify(stockRepository).saveAll(stocks);
    }

    @Test
    @DisplayName("[검색은 검색 캐시로 위임한다]")
    void searchStocks_delegatesToCache() {
        Pageable pageable = PageRequest.of(0, 10);
        Slice<Stock> slice = new SliceImpl<>(List.of(stock("005930")), pageable, false);
        given(stockSearchCache.search("삼성", pageable)).willReturn(slice);

        assertThat(service.searchStocks("삼성", pageable)).isSameAs(slice);
    }

    @Test
    @DisplayName("[코드 목록 순서를 그대로 유지하고, 조회되지 않은 코드는 건너뛴다]")
    void getStocksByCodesInOrder_keepsInputOrderAndDropsMissing() {
        List<String> codes = List.of("000660", "999999", "005930");
        // IN 조회 결과는 입력 순서를 보장하지 않으므로 일부러 다른 순서로 돌려준다
        given(stockRepository.findByStockCodeIn(codes)).willReturn(List.of(stock("005930"), stock("000660")));

        assertThat(service.getStocksByCodesInOrder(codes))
            .extracting(Stock::getStockCode).containsExactly("000660", "005930");
    }
}
