package com.quantlime.market.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.DailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.price.service.PriceGapFillService;
import com.quantlime.score.domain.Score;
import com.quantlime.score.repository.ScoreRepository;
import com.quantlime.score.service.ScoreService;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarketDataRefreshServiceTest {

    private static final String DOMESTIC_CODE = "005930";
    private static final String OVERSEAS_CODE = "AAPL";

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @Mock
    private OverseasDailyPriceRepository overseasDailyPriceRepository;

    @Mock
    private ScoreRepository scoreRepository;

    @Mock
    private PriceGapFillService priceGapFillService;

    @Mock
    private ScoreService scoreService;

    @Mock
    private TaskExecutor domesticMarketDataRefreshTaskExecutor;

    @Mock
    private TaskExecutor overseasMarketDataRefreshTaskExecutor;

    // @InjectMocks의 생성자 주입은 타입이 같은 목(TaskExecutor)이 2개면
    // 이름이 아니라 타입만으로 매칭을 시도하다 둘 다 같은 목으로 잘못
    // 엮일 수 있어(실제로 재현됨), 여기서는 명시적으로 생성자를 호출한다.
    private MarketDataRefreshService marketDataRefreshService;

    @BeforeEach
    void setUp() {
        marketDataRefreshService = new MarketDataRefreshService(
            stockMasterService, dailyPriceRepository, overseasDailyPriceRepository,
            scoreRepository, priceGapFillService, scoreService,
            domesticMarketDataRefreshTaskExecutor, overseasMarketDataRefreshTaskExecutor);
    }

    /**
     * 실행기가 제출된 작업을 즉시 동기 실행하도록 스텁한다 - 실제 병렬 실행
     * 여부가 아니라 각 경로의 로직만 검증하면 충분하다. refreshStock()은
     * 실행기를 타지 않으므로(refreshAll()만 해당) 해당 테스트에서만 스텁한다.
     */
    private void stubExecutorsToRunSynchronously() {
        willAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).given(domesticMarketDataRefreshTaskExecutor).execute(any());
        willAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).given(overseasMarketDataRefreshTaskExecutor).execute(any());
    }

    @Test
    @DisplayName("[전체 상장종목을 국내/해외로 나눠 각자 가격 갭필 후, 갱신이 필요한 종목만 모아 스코어를 재계산한다]")
    void refreshAll_splitsIntoDomesticAndOverseasAndRefreshesStaleScoresOnly() {
        // given
        stubExecutorsToRunSynchronously();
        Stock domestic = Stock.of(DOMESTIC_CODE, "삼성전자", MarketType.KOSPI, ListingStatus.LISTED, "전기전자");
        Stock overseas = Stock.of(OVERSEAS_CODE, "APPLE INC", MarketType.NASDAQ, ListingStatus.LISTED, "720");
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(domestic, overseas));

        LocalDate today = LocalDate.now();
        given(dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(DOMESTIC_CODE))
            .willReturn(Optional.of(dailyPrice(today)));
        given(overseasDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(OVERSEAS_CODE))
            .willReturn(Optional.of(overseasDailyPrice(today)));
        // 국내는 스코어가 가격 최신일보다 뒤처져 재계산 대상, 해외는 이미 최신이라 제외
        given(scoreRepository.findTopByStockCodeOrderByScoreDateDesc(DOMESTIC_CODE))
            .willReturn(Optional.of(score(today.minusDays(1))));
        given(scoreRepository.findTopByStockCodeOrderByScoreDateDesc(OVERSEAS_CODE))
            .willReturn(Optional.of(score(today)));

        // when
        marketDataRefreshService.refreshAll();

        // then
        verify(priceGapFillService).fillDomesticGap(DOMESTIC_CODE);
        verify(priceGapFillService).fillOverseasGap(OVERSEAS_CODE, "NAS");

        // 국내는 국내 전용 스코어 재계산에 삼성전자만 포함
        ArgumentCaptor<List<String>> domesticScoreCaptor = ArgumentCaptor.forClass(List.class);
        verify(scoreService).recalculateScores(domesticScoreCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(domesticScoreCaptor.getValue()).containsExactly(DOMESTIC_CODE);

        // 해외는 이미 최신이라 해외 전용 스코어 재계산 대상에서 제외
        ArgumentCaptor<List<String>> overseasScoreCaptor = ArgumentCaptor.forClass(List.class);
        verify(scoreService).recalculateOverseasScores(overseasScoreCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(overseasScoreCaptor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("[단건 갱신은 종목의 시장 구분에 따라 국내/해외 경로 중 하나만 탄다]")
    void refreshStock_domesticStock_usesDomesticPath() {
        // given
        Stock domestic = Stock.of(DOMESTIC_CODE, "삼성전자", MarketType.KOSPI, ListingStatus.LISTED, "전기전자");
        given(stockMasterService.getStockByCode(DOMESTIC_CODE)).willReturn(domestic);
        given(dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(DOMESTIC_CODE))
            .willReturn(Optional.empty());

        // when
        marketDataRefreshService.refreshStock(DOMESTIC_CODE);

        // then
        verify(priceGapFillService).fillDomesticGap(DOMESTIC_CODE);
        verify(priceGapFillService, never()).fillOverseasGap(any(), any());
    }

    private DailyPrice dailyPrice(LocalDate tradeDate) {
        return DailyPrice.of(DOMESTIC_CODE, tradeDate, 70000L, 71000L, 69000L, 70500L, 1000000L);
    }

    private OverseasDailyPrice overseasDailyPrice(LocalDate tradeDate) {
        return OverseasDailyPrice.of(OVERSEAS_CODE, tradeDate, 150.0, 152.0, 148.0, 151.0, 1000000L);
    }

    private Score score(LocalDate scoreDate) {
        return Score.of(DOMESTIC_CODE, scoreDate, 50.0, 50.0, 50.0, null, null, null, false);
    }
}
