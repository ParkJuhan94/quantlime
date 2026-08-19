package com.quantlime.stock.cache;

import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 검색 시맨틱(대소문자 규칙 포함)은 기존
 * {@code StockRepositoryTest}가 검증하던 것과 동일하게 유지돼야 한다 -
 * 여기서는 같은 시나리오를 인메모리 캐시 버전으로 재검증한다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockSearchCacheTest {

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private StockSearchCache stockSearchCache;

    private void seed(Stock... stocks) {
        given(stockRepository.findAll()).willReturn(List.of(stocks));
    }

    @Test
    @DisplayName("[해외 종목은 한글명으로도 검색된다]")
    void search_byKoreanName_findsOverseasStock() {
        seed(StockFixture.createStock("005930", "삼성전자"),
            withKoreanName(StockFixture.createOverseasStock("AAPL", "APPLE INC"), "애플"));

        Slice<Stock> result = stockSearchCache.search("애플", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Stock::getStockCode).containsExactly("AAPL");
    }

    @Test
    @DisplayName("[해외 종목은 영문명으로도 검색된다 - 대소문자 구분 없음]")
    void search_byEnglishName_isCaseInsensitive() {
        seed(StockFixture.createOverseasStock("AAPL", "APPLE INC"));

        Slice<Stock> result = stockSearchCache.search("apple", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Stock::getStockCode).containsExactly("AAPL");
    }

    @Test
    @DisplayName("[종목코드는 대소문자를 구분한다 - 기존 리포지토리 메서드와 동일한 시맨틱]")
    void search_byStockCode_isCaseSensitive() {
        seed(StockFixture.createOverseasStock("AAPL", "APPLE INC"));

        Slice<Stock> exactCase = stockSearchCache.search("AAPL", PageRequest.of(0, 10));
        Slice<Stock> lowerCase = stockSearchCache.search("aapl", PageRequest.of(0, 10));

        assertThat(exactCase.getContent()).extracting(Stock::getStockCode).containsExactly("AAPL");
        assertThat(lowerCase.getContent()).isEmpty();
    }

    @Test
    @DisplayName("[국내 종목은 koreanName이 없어도 종목명으로 검색된다]")
    void search_domesticStock_matchesOnStockNameOnly() {
        seed(StockFixture.createStock("005930", "삼성전자"));

        Slice<Stock> result = stockSearchCache.search("삼성전자", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Stock::getStockCode).containsExactly("005930");
    }

    @Test
    @DisplayName("[일치하는 종목이 없으면 빈 결과를 반환한다]")
    void search_noMatch_returnsEmpty() {
        seed(StockFixture.createStock("005930", "삼성전자"));

        Slice<Stock> result = stockSearchCache.search("존재하지않음", PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("[페이지 크기만큼 잘라 반환하고 다음 페이지 존재 여부를 표시한다]")
    void search_paginatesAndReportsHasNext() {
        seed(
            StockFixture.createStock("000010", "가나다전자"),
            StockFixture.createStock("000020", "가나다물산"),
            StockFixture.createStock("000030", "가나다중공업"));

        Slice<Stock> firstPage = stockSearchCache.search("가나다", PageRequest.of(0, 2));
        Slice<Stock> secondPage = stockSearchCache.search("가나다", PageRequest.of(1, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 DB를 다시 조회하지 않는다]")
    void search_withinTtl_doesNotRefetch() {
        seed(StockFixture.createStock("005930", "삼성전자"));

        stockSearchCache.search("삼성", PageRequest.of(0, 10));
        stockSearchCache.search("삼성", PageRequest.of(0, 10));

        verify(stockRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("[TTL이 지나면 기존 값을 즉시 반환하면서 백그라운드로 갱신한다(stale-while-revalidate)]")
    void search_afterTtlExpired_servesStaleAndRefreshesInBackground() {
        seed(StockFixture.createStock("005930", "삼성전자"));
        stockSearchCache.search("삼성", PageRequest.of(0, 10));

        // when: 마지막 갱신 시각을 TTL 밖으로 되돌려 만료 상태를 재현
        ReflectionTestUtils.setField(stockSearchCache, "lastRefreshedAt", Instant.now().minusSeconds(601));
        Slice<Stock> result = stockSearchCache.search("삼성", PageRequest.of(0, 10));

        // then: 이 호출 자체는 갱신을 기다리지 않고 기존(stale) 값을 즉시 반환
        assertThat(result.getContent()).extracting(Stock::getStockCode).containsExactly("005930");
        // 백그라운드 갱신은 비동기라 즉시 검증할 수 없으므로 짧은 타임아웃 안에서 확인
        verify(stockRepository, timeout(1000).times(2)).findAll();
    }

    private Stock withKoreanName(Stock stock, String koreanName) {
        stock.updateKoreanName(koreanName);
        return stock;
    }
}
