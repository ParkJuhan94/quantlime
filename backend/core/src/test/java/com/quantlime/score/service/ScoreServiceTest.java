package com.quantlime.score.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.ScoreBatchApiRequest;
import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse;
import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse.DailyScoreSeriesApiResponse;
import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse.DivergenceApiResponse;
import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse.StockScoreSeriesApiResponse;
import com.quantlime.infra.python.exception.PythonEngineErrorCode;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.repository.StockLiquidityRepository;
import com.quantlime.price.service.DomesticDailyPriceService;
import com.quantlime.score.cache.ScoreRankingCacheStore;
import com.quantlime.score.domain.Divergence;
import com.quantlime.score.domain.Quadrant;
import com.quantlime.score.domain.Score;
import com.quantlime.score.dto.response.ScoreRankingResponse;
import com.quantlime.score.dto.response.ScoreResponse;
import com.quantlime.score.repository.ScoreRepository;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import com.quantlime.watchlist.repository.WatchlistRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private DomesticDailyPriceService domesticDailyPriceService;

    @Mock
    private PythonEngineClient pythonEngineClient;

    @Mock
    private ScorePersistenceService scorePersistenceService;

    @Mock
    private ScoreRepository scoreRepository;

    @Mock
    private StockLiquidityRepository stockLiquidityRepository;

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private ScoreRankingCacheStore scoreRankingCacheStore;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private ScoreService scoreService;

    @Test
    @DisplayName("[OHLCV 이력이 없으면 퀀트 엔진을 호출하지 않는다]")
    void recalculateScore_noDailyPrices_skipsPythonCall() {
        // given
        given(domesticDailyPriceService.getDailyPrices(anyList(), any(), any()))
            .willReturn(List.of());

        // when
        scoreService.recalculateDomesticScore(STOCK_CODE);

        // then
        verify(pythonEngineClient, never()).calculateScoreSeries(any());
        verify(scorePersistenceService, never()).saveAll(any());
    }

    @Test
    @DisplayName("[OHLCV 이력이 있으면 퀀트 엔진을 호출하고 결과를 저장에 위임한다]")
    void recalculateScore_hasDailyPrices_callsPythonAndDelegatesPersistence() {
        // given
        given(domesticDailyPriceService.getDailyPrices(anyList(), any(), any()))
            .willReturn(List.of(domesticDailyPrice(LocalDate.of(2026, 7, 3))));
        ScoreSeriesBatchApiResponse response =
            new ScoreSeriesBatchApiResponse(List.of(successResponse(STOCK_CODE, 82.0)));
        given(pythonEngineClient.calculateScoreSeries(any(ScoreBatchApiRequest.class)))
            .willReturn(response);

        // when
        scoreService.recalculateDomesticScore(STOCK_CODE);

        // then
        verify(scorePersistenceService).saveAll(response.scores());
    }

    @Test
    @DisplayName("[퀀트 엔진 호출이 실패하면 예외가 그대로 전파되고 저장은 위임되지 않는다]")
    void recalculateScore_pythonEngineFails_propagatesAndSkipsPersistence() {
        // given
        given(domesticDailyPriceService.getDailyPrices(anyList(), any(), any()))
            .willReturn(List.of(domesticDailyPrice(LocalDate.of(2026, 7, 3))));
        given(pythonEngineClient.calculateScoreSeries(any(ScoreBatchApiRequest.class)))
            .willThrow(new ExternalApiException(PythonEngineErrorCode.SCORE_CALCULATION_FAILED));

        // when & then: 예외는 상위(WatchlistService/스케줄러)에서 잡으므로 여기선 전파돼야 함
        assertThatThrownBy(() -> scoreService.recalculateDomesticScore(STOCK_CODE))
            .isInstanceOf(ExternalApiException.class);
        verify(scorePersistenceService, never()).saveAll(any());
    }

    @Test
    @DisplayName("[상장 종목이 없으면 일괄 재계산 시 퀀트 엔진을 호출하지 않는다]")
    void recalculateAllListedScores_noListedStocks_skipsPythonCall() {
        // given
        given(stockMasterService.getAllListedStocks()).willReturn(List.of());

        // when
        scoreService.recalculateAllListedScores();

        // then
        verify(pythonEngineClient, never()).calculateScoreSeries(any());
    }

    @Test
    @DisplayName("[상장 종목이 있으면 일괄 조회 후 결과 저장을 위임한다]")
    void recalculateAllListedScores_withListedStocks_delegatesPersistenceOfAllResults() {
        // given
        String secondCode = "000660";
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(
            StockFixture.createStock(STOCK_CODE, "삼성전자"), StockFixture.createStock(secondCode, "SK하이닉스")));
        given(domesticDailyPriceService.getDailyPrices(anyList(), any(), any()))
            .willReturn(List.of(domesticDailyPrice(STOCK_CODE), domesticDailyPrice(secondCode)));
        ScoreSeriesBatchApiResponse response = new ScoreSeriesBatchApiResponse(List.of(
            successResponse(STOCK_CODE, 70.0), successResponse(secondCode, 60.0)));
        given(pythonEngineClient.calculateScoreSeries(any(ScoreBatchApiRequest.class)))
            .willReturn(response);

        // when
        scoreService.recalculateAllListedScores();

        // then
        verify(scorePersistenceService).saveAll(response.scores());
    }

    @Test
    @DisplayName("[일부 종목만 OHLCV 이력이 있으면 이력 없는 종목은 배치 요청에서 제외한다]")
    void recalculateAllListedScores_someStocksHaveNoHistory_excludesThemFromRequest() {
        // given: 방금 등록되어 백필이 아직 안 끝난 종목은 이력이 0건일 수 있다.
        // 이걸 그대로 요청에 포함하면 퀀트 엔진이 빈 OHLCV 때문에 실패해
        // 같은 배치의 다른 종목 스코어까지 갱신되지 못하므로, 사전에 제외해야 한다.
        String noHistoryCode = "000660";
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(
            StockFixture.createStock(STOCK_CODE, "삼성전자"), StockFixture.createStock(noHistoryCode, "SK하이닉스")));
        given(domesticDailyPriceService.getDailyPrices(anyList(), any(), any()))
            .willReturn(List.of(domesticDailyPrice(STOCK_CODE)));
        given(pythonEngineClient.calculateScoreSeries(any(ScoreBatchApiRequest.class)))
            .willReturn(new ScoreSeriesBatchApiResponse(List.of(successResponse(STOCK_CODE, 70.0))));

        // when
        scoreService.recalculateAllListedScores();

        // then
        ArgumentCaptor<ScoreBatchApiRequest> requestCaptor =
            ArgumentCaptor.forClass(ScoreBatchApiRequest.class);
        verify(pythonEngineClient).calculateScoreSeries(requestCaptor.capture());
        assertThat(requestCaptor.getValue().stocks())
            .extracting(ScoreBatchApiRequest.StockScoreApiRequest::stockCode)
            .containsExactly(STOCK_CODE);
    }

    @Test
    @DisplayName("[대상 종목이 청크 크기를 넘으면 여러 번 나눠 호출하고, 한 청크가 실패해도 나머지는 계속 진행한다]")
    void recalculateAllListedScores_exceedsChunkSize_splitsIntoMultipleBatchCallsAndIsolatesFailure() {
        // given: 청크 크기(100)를 넘는 101개 종목 - 마지막 종목만 별도 청크로 분리돼야 한다
        List<Stock> stocks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            stocks.add(StockFixture.createStock("A%05d".formatted(i), "종목" + i));
        }
        stocks.add(StockFixture.createStock(STOCK_CODE, "삼성전자"));
        given(stockMasterService.getAllListedStocks()).willReturn(stocks);
        given(domesticDailyPriceService.getDailyPrices(anyList(), any(), any()))
            .willReturn(List.of(domesticDailyPrice(STOCK_CODE)));
        // 첫 청크(100개)는 실패, 두 번째 청크(1개)는 성공 - 첫 청크 실패가 두 번째 청크 실행을 막지 않아야 함
        given(pythonEngineClient.calculateScoreSeries(any(ScoreBatchApiRequest.class)))
            .willThrow(new ExternalApiException(PythonEngineErrorCode.SCORE_CALCULATION_FAILED))
            .willReturn(new ScoreSeriesBatchApiResponse(List.of(successResponse(STOCK_CODE, 70.0))));

        // when
        scoreService.recalculateAllListedScores();

        // then
        verify(pythonEngineClient, times(2)).calculateScoreSeries(any());
        verify(scorePersistenceService, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("[저장된 스코어가 있으면 조회한다]")
    void getScore_found_returnsResponse() {
        // given
        Score score = Score.of(STOCK_CODE, LocalDate.now(), 80.0, 40.0, 65.0,
            null, null, Divergence.of(false, null), false);
        given(scoreRepository.findTopByStockCodeOrderByScoreDateDesc(STOCK_CODE))
            .willReturn(Optional.of(score));

        // when
        ScoreResponse response = scoreService.getScore(STOCK_CODE);

        // then
        assertThat(response.stockCode()).isEqualTo(STOCK_CODE);
        assertThat(response.compositeScore()).isEqualTo(65.0);
    }

    @Test
    @DisplayName("[사분면이 저장돼 있으면 한글 표시명으로 변환해 응답한다]")
    void getScore_withQuadrant_returnsQuadrantLabel() {
        // given
        Score score = Score.of(STOCK_CODE, LocalDate.now(), 80.0, 40.0, 65.0,
            null, Quadrant.TREND_UP_OVERSOLD, Divergence.of(false, null), false);
        given(scoreRepository.findTopByStockCodeOrderByScoreDateDesc(STOCK_CODE))
            .willReturn(Optional.of(score));

        // when
        ScoreResponse response = scoreService.getScore(STOCK_CODE);

        // then
        assertThat(response.quadrant()).isEqualTo("상승추세 눌림목");
    }

    @Test
    @DisplayName("[저장된 스코어가 없으면 예외가 발생한다]")
    void getScore_notFound_throwsNotFoundException() {
        // given
        given(scoreRepository.findTopByStockCodeOrderByScoreDateDesc(STOCK_CODE))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> scoreService.getScore(STOCK_CODE))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("[전 상장종목 스코어 상위 N개를 종목명·섹터와 함께 반환한다]")
    void getAllStocksScoreRanking_returnsTopScoresWithStockInfo() {
        // given
        Score score = Score.of(STOCK_CODE, LocalDate.now(), 80.0, 40.0, 90.0,
            null, null, Divergence.of(false, null), false);
        Stock stock = StockFixture.createStock(STOCK_CODE, "삼성전자");
        given(scoreRankingCacheStore.find("all")).willReturn(Optional.empty());
        // 캐시 미스면 limit과 무관하게 항상 캐시 상한(50건, ScoreController의
        // @Max(50)과 맞춘 값)만큼 조회해 캐싱한 뒤 요청받은 limit만큼 자른다
        // (2026-09 성능 감사 - ScoreRankingCacheStore 참고).
        given(scoreRepository.findTopScoresOrderByCompositeScoreDesc(50, null)).willReturn(List.of(score));
        given(stockMasterService.getStocksByCodesInOrder(List.of(STOCK_CODE))).willReturn(List.of(stock));

        // when
        var result = scoreService.getAllStocksScoreRanking(10, "all");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).stockCode()).isEqualTo(STOCK_CODE);
        assertThat(result.get(0).stockName()).isEqualTo("삼성전자");
        assertThat(result.get(0).compositeScore()).isEqualTo(90.0);
        verify(scoreRankingCacheStore).save("all", result);
    }

    // 2026-09 성능 감사 중 curl로 20개 동시 요청을 캐시 비운 직후 쏴서
    // 재현한 캐시 스탬피드(9개가 동시에 DB를 때려 HikariCP active가
    // 풀 전체(10/10)까지 참) - rankingCacheLoadLocks 단일 비행 락으로
    // 수정한 회귀 방지 테스트. Mockito 목은 상태가 없어 save() 호출을
    // AtomicReference에 반영하도록 스텁해야 더블체크 락 안에서 "이미
    // 채워짐"을 실제로 관찰할 수 있다.
    @Test
    @DisplayName("[캐시 미스가 동시에 여러 건 들어와도 DB 조회는 한 번만 실행한다(단일 비행)]")
    void getAllStocksScoreRanking_concurrentCacheMiss_queriesOnlyOnce() throws Exception {
        // given
        Score score = Score.of(STOCK_CODE, LocalDate.now(), 80.0, 40.0, 90.0,
            null, null, Divergence.of(false, null), false);
        Stock stock = StockFixture.createStock(STOCK_CODE, "삼성전자");
        AtomicReference<List<ScoreRankingResponse>> cached = new AtomicReference<>();
        given(scoreRankingCacheStore.find("all"))
            .willAnswer(invocation -> Optional.ofNullable(cached.get()));
        willAnswer(invocation -> {
            cached.set(invocation.getArgument(1));
            return null;
        }).given(scoreRankingCacheStore).save(eq("all"), any());
        given(scoreRepository.findTopScoresOrderByCompositeScoreDesc(50, null)).willReturn(List.of(score));
        given(stockMasterService.getStocksByCodesInOrder(List.of(STOCK_CODE))).willReturn(List.of(stock));

        int concurrency = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        try {
            // when: concurrency개 스레드가 전부 준비될 때까지 기다렸다가
            // 동시에 출발시켜 실제 동시 요청을 재현한다.
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    scoreService.getAllStocksScoreRanking(10, "all");
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // then: 20개 스레드가 동시에 캐시 미스로 진입해도 실제 DB 조회·
        // 캐시 저장은 각각 정확히 1회뿐이다.
        verify(scoreRepository, times(1)).findTopScoresOrderByCompositeScoreDesc(50, null);
        verify(scoreRankingCacheStore, times(1)).save(eq("all"), any());
    }

    private DomesticDailyPrice domesticDailyPrice(LocalDate tradeDate) {
        return DomesticDailyPrice.of(STOCK_CODE, tradeDate, 70000L, 71000L, 69000L, 70500L, 1000000L);
    }

    private DomesticDailyPrice domesticDailyPrice(String stockCode) {
        return DomesticDailyPrice.of(stockCode, LocalDate.of(2026, 7, 3),
            70000L, 71000L, 69000L, 70500L, 1000000L);
    }

    private StockScoreSeriesApiResponse successResponse(String stockCode, double compositeScore) {
        DailyScoreSeriesApiResponse dailyScore = new DailyScoreSeriesApiResponse(
            LocalDate.now().toString(), 70.0, 50.0, compositeScore, "A", "trend_up_oversold",
            new DivergenceApiResponse(false, null), false);
        return new StockScoreSeriesApiResponse(stockCode, List.of(dailyScore));
    }
}
