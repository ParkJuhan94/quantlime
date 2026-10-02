package com.quantlime.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
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

/**
 * 기동 시 종목 마스터를 한 번만 적재하는 러너다. CSV는 core 테스트 리소스(data/krx-stocks.csv)의 샘플을 쓴다:
 * 정상 3행(공백 포함 1행), 알 수 없는 시장 1행, 컬럼 부족 1행.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockMasterInitializerTest {

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private StockMasterInitializer initializer;

    @Test
    @DisplayName("[이미 종목이 적재돼 있으면 CSV를 읽지 않고 저장하지 않는다 - 재기동마다 중복 적재 방지]")
    void run_alreadyLoaded_skips() {
        given(stockRepository.count()).willReturn(2500L);

        initializer.run(null);

        verify(stockRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("[비어 있으면 CSV를 파싱해 정상 행만 LISTED 상태로 저장한다(알 수 없는 시장·컬럼 부족 행은 건너뛰고 공백은 제거)]")
    @SuppressWarnings("unchecked")
    void run_empty_loadsCsvAndSkipsInvalidRows() {
        given(stockRepository.count()).willReturn(0L);

        initializer.run(null);

        ArgumentCaptor<List<Stock>> captor = ArgumentCaptor.forClass(List.class);
        verify(stockRepository).saveAll(captor.capture());
        List<Stock> saved = captor.getValue();
        assertThat(saved).extracting(Stock::getStockCode).containsExactly("005930", "247540", "000660");
        assertThat(saved).extracting(Stock::getStockName).containsExactly("삼성전자", "에코프로비엠", "SK하이닉스");
        assertThat(saved).extracting(Stock::getMarketType)
            .containsExactly(MarketType.KOSPI, MarketType.KOSDAQ, MarketType.KOSPI);
        assertThat(saved).extracting(Stock::getListingStatus).containsOnly(ListingStatus.LISTED);
    }

    @Test
    @DisplayName("[저장 중 예외가 나도 앱 기동을 막지 않는다(로그만 남김)]")
    void run_saveFails_doesNotPropagate() {
        given(stockRepository.count()).willReturn(0L);
        willThrow(new IllegalStateException("db down")).given(stockRepository).saveAll(anyList());

        assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
    }
}
