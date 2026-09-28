package com.quantlime.market.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.lock.RedisLockService;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import com.quantlime.market.event.PriceRefreshRequestedEvent;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.price.service.PriceGapFillService;
import com.quantlime.price.service.StockLiquidityService;
import com.quantlime.score.cache.ScoreRankingCacheStore;
import com.quantlime.score.domain.PeerGroup;
import com.quantlime.score.repository.ScoreRepository;
import com.quantlime.score.service.ScoreService;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.OverseasStockMasterSyncService;
import com.quantlime.stock.service.StockMasterService;
import com.quantlime.stock.service.DomesticStockMasterSyncService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarketDataRefreshServiceTest {

    private static final String DOMESTIC_CODE = "005930";
    private static final String OVERSEAS_CODE = "AAPL";

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private DomesticStockMasterSyncService domesticStockMasterSyncService;

    @Mock
    private OverseasStockMasterSyncService overseasStockMasterSyncService;

    @Mock
    private DomesticDailyPriceRepository domesticDailyPriceRepository;

    @Mock
    private OverseasDailyPriceRepository overseasDailyPriceRepository;

    @Mock
    private ScoreRepository scoreRepository;

    @Mock
    private PriceGapFillService priceGapFillService;

    @Mock
    private StockLiquidityService stockLiquidityService;

    @Mock
    private ScoreService scoreService;

    @Mock
    private ScoreRankingCacheStore scoreRankingCacheStore;

    @Mock
    private BenchmarkIndexBackfillService benchmarkIndexBackfillService;

    @Mock
    private InvestorTradingBackfillService investorTradingBackfillService;

    @Mock
    private RedisLockService redisLockService;

    @Mock
    private PriceRefreshBatchGate priceRefreshBatchGate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MarketDataRefreshService marketDataRefreshService;

    @BeforeEach
    void setUp() {
        marketDataRefreshService = new MarketDataRefreshService(
            stockMasterService, domesticStockMasterSyncService, overseasStockMasterSyncService,
            domesticDailyPriceRepository, overseasDailyPriceRepository,
            scoreRepository, priceGapFillService, stockLiquidityService, scoreService,
            scoreRankingCacheStore, benchmarkIndexBackfillService, investorTradingBackfillService,
            redisLockService, priceRefreshBatchGate, eventPublisher);
    }

    /**
     * redisLockService.runExclusively(key, ttl, task)가 실제로 락을 잡은
     * 것처럼 task를 즉시 실행하도록 스텁한다 - refreshAllExclusively()가
     * refreshAll()을 제대로 감싸 호출하는지만 검증하면 되고, 실제 Redis
     * SETNX 동작 자체는 RedisLockService 자체 테스트의 책임이다.
     */
    private void stubLockToRunTask() {
        given(redisLockService.runExclusively(any(), any(), any()))
            .willAnswer(invocation -> {
                Supplier<Boolean> task = invocation.getArgument(2);
                return Optional.of(task.get());
            });
    }

    @Test
    @DisplayName("[전체 상장종목을 국내/해외로 나눠 종목별 가격 갱신 이벤트를 fan-out 발행하고, "
        + "배치 완료까지 대기한 뒤 배치 단위 후속작업(유동성/정규화/캐시)을 정확히 1회 수행한다]")
    void refreshAll_publishesFanOutEventsAndRunsBatchFollowUpAfterCompletion() {
        // given
        Stock domestic = Stock.of(DOMESTIC_CODE, "삼성전자", MarketType.KOSPI, ListingStatus.LISTED, "전기전자");
        Stock overseas = Stock.of(OVERSEAS_CODE, "APPLE INC", MarketType.NASDAQ, ListingStatus.LISTED, "720");
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(domestic, overseas));
        LocalDate today = LocalDate.now();
        // 국내는 스코어가 뒤처져 있고(스냅샷에 값 존재), 해외는 스냅샷에 값이
        // 아예 없는 경우(null) - 둘 다 이벤트 payload에 그대로 실려야 한다.
        given(scoreRepository.findLatestScoreDateByStockCode())
            .willReturn(Map.of(DOMESTIC_CODE, today.minusDays(1)));
        given(priceRefreshBatchGate.awaitCompletion(any(), any(), any())).willReturn(true);

        // when
        marketDataRefreshService.refreshAll();

        // then
        verify(domesticStockMasterSyncService).syncStockMaster();
        verify(overseasStockMasterSyncService).syncAll();

        verify(priceRefreshBatchGate).startBatch(any(), eq(PeerGroup.DOMESTIC), eq(1));
        verify(priceRefreshBatchGate).startBatch(any(), eq(PeerGroup.OVERSEAS), eq(1));

        ArgumentCaptor<PriceRefreshRequestedEvent> eventCaptor =
            ArgumentCaptor.forClass(PriceRefreshRequestedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        List<PriceRefreshRequestedEvent> events = eventCaptor.getAllValues();

        PriceRefreshRequestedEvent domesticEvent = events.stream()
            .filter(e -> e.stockCode().equals(DOMESTIC_CODE)).findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(domesticEvent.peerGroup()).isEqualTo(PeerGroup.DOMESTIC);
        org.assertj.core.api.Assertions.assertThat(domesticEvent.latestScoreDate()).isEqualTo(today.minusDays(1));

        PriceRefreshRequestedEvent overseasEvent = events.stream()
            .filter(e -> e.stockCode().equals(OVERSEAS_CODE)).findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(overseasEvent.peerGroup()).isEqualTo(PeerGroup.OVERSEAS);
        org.assertj.core.api.Assertions.assertThat(overseasEvent.latestScoreDate()).isNull();

        // 두 이벤트가 같은 runId를 공유해야 같은 배치로 취급된다.
        org.assertj.core.api.Assertions.assertThat(domesticEvent.runId()).isEqualTo(overseasEvent.runId());

        verify(priceRefreshBatchGate).awaitCompletion(eq(domesticEvent.runId()), eq(PeerGroup.DOMESTIC), any());
        verify(priceRefreshBatchGate).awaitCompletion(eq(domesticEvent.runId()), eq(PeerGroup.OVERSEAS), any());

        // fan-out 자체는 gap-fill을 하지 않는다 - 그건 컨슈머(다른 스레드/모듈)의 몫.
        verify(priceGapFillService, never()).fillDomesticGap(any());
        verify(priceGapFillService, never()).fillOverseasGap(any());

        // 배치 완료 대기 이후에만 배치 단위 후속작업이 정확히 1회 수행된다.
        verify(stockLiquidityService).refreshDomestic(any());
        verify(stockLiquidityService).refreshOverseas(any());
        verify(scoreService).normalizeCrossSection(PeerGroup.DOMESTIC);
        verify(scoreService).normalizeCrossSection(PeerGroup.OVERSEAS);
        verify(scoreRankingCacheStore).evictAll();
        verify(benchmarkIndexBackfillService).refreshRecentIfNeeded();
        verify(investorTradingBackfillService).refreshAllIfNeeded();
    }

    @Test
    @DisplayName("[국내 종목이 Toss stock-not-found(404)를 내면 가격 미커버로 표시해 이후 대상에서 제외한다]")
    void refreshSingleStockFromFanOut_domesticStockNotFound_marksPriceUnsupported() {
        // given
        Stock domestic = Stock.of(DOMESTIC_CODE, "삼성전자", MarketType.KOSPI, ListingStatus.LISTED, "전기전자");
        given(stockMasterService.getStockByCode(DOMESTIC_CODE)).willReturn(domestic);
        HttpClientErrorException notFound = HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
        given(priceGapFillService.fillDomesticGap(DOMESTIC_CODE))
            .willThrow(new ExternalApiException(TossApiErrorCode.CANDLE_INQUIRY_FAILED, notFound));

        // when
        PeerGroup peerGroup = marketDataRefreshService.refreshSingleStockFromFanOut(DOMESTIC_CODE, null);

        // then
        org.assertj.core.api.Assertions.assertThat(peerGroup).isEqualTo(PeerGroup.DOMESTIC);
        verify(stockMasterService).markPriceUnsupported(DOMESTIC_CODE);
    }

    @Test
    @DisplayName("[국내 종목이 404가 아닌 실패를 내면 미커버로 표시하지 않는다(다음 기동 재시도)]")
    void refreshSingleStockFromFanOut_domesticNon404Failure_doesNotMark() {
        // given
        Stock domestic = Stock.of(DOMESTIC_CODE, "삼성전자", MarketType.KOSPI, ListingStatus.LISTED, "전기전자");
        given(stockMasterService.getStockByCode(DOMESTIC_CODE)).willReturn(domestic);
        given(priceGapFillService.fillDomesticGap(DOMESTIC_CODE))
            .willThrow(new ExternalApiException(TossApiErrorCode.RATE_LIMIT_EXCEEDED));

        // when
        marketDataRefreshService.refreshSingleStockFromFanOut(DOMESTIC_CODE, null);

        // then
        verify(stockMasterService, never()).markPriceUnsupported(any());
    }

    @Test
    @DisplayName("[해외 종목이 Toss stock-not-found(404)를 내면 가격 미커버로 표시해 이후 대상에서 제외한다]")
    void refreshSingleStockFromFanOut_overseasStockNotFound_marksPriceUnsupported() {
        // given
        Stock overseas = Stock.of(OVERSEAS_CODE, "APPLE INC", MarketType.NASDAQ, ListingStatus.LISTED, "720");
        given(stockMasterService.getStockByCode(OVERSEAS_CODE)).willReturn(overseas);
        HttpClientErrorException notFound = HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
        given(priceGapFillService.fillOverseasGap(OVERSEAS_CODE))
            .willThrow(new ExternalApiException(TossApiErrorCode.CANDLE_INQUIRY_FAILED, notFound));

        // when
        PeerGroup peerGroup = marketDataRefreshService.refreshSingleStockFromFanOut(OVERSEAS_CODE, null);

        // then
        org.assertj.core.api.Assertions.assertThat(peerGroup).isEqualTo(PeerGroup.OVERSEAS);
        verify(stockMasterService).markPriceUnsupported(OVERSEAS_CODE);
    }

    @Test
    @DisplayName("[해외 종목이 Toss 400(심볼 형식 미지원)을 내면 가격 미커버로 표시한다 - "
        + "\"AAC/UN\" 같은 \"/\" 포함 심볼이 404가 아닌 400으로 거부되며 실제로 겪은 무한 반복 버그]")
    void refreshSingleStockFromFanOut_overseasUnsupportedSymbolFormat_marksPriceUnsupported() {
        // given
        Stock overseas = Stock.of("AAC/UN", "SOME SPAC UNIT", MarketType.NYSE, ListingStatus.LISTED, "720");
        given(stockMasterService.getStockByCode("AAC/UN")).willReturn(overseas);
        HttpClientErrorException badRequest = HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null);
        given(priceGapFillService.fillOverseasGap("AAC/UN"))
            .willThrow(new ExternalApiException(TossApiErrorCode.CANDLE_INQUIRY_FAILED, badRequest));

        // when
        marketDataRefreshService.refreshSingleStockFromFanOut("AAC/UN", null);

        // then
        verify(stockMasterService).markPriceUnsupported("AAC/UN");
    }

    @Test
    @DisplayName("[해외 종목이 404가 아닌 실패를 내면 미커버로 표시하지 않는다(다음 기동 재시도) - "
        + "이 안전장치가 없으면 레이트리밋 실패가 매 스윕마다 계속 반복돼 다른 종목의 예산까지 갉아먹는다]")
    void refreshSingleStockFromFanOut_overseasNon404Failure_doesNotMark() {
        // given
        Stock overseas = Stock.of(OVERSEAS_CODE, "APPLE INC", MarketType.NASDAQ, ListingStatus.LISTED, "720");
        given(stockMasterService.getStockByCode(OVERSEAS_CODE)).willReturn(overseas);
        given(priceGapFillService.fillOverseasGap(OVERSEAS_CODE))
            .willThrow(new ExternalApiException(TossApiErrorCode.RATE_LIMIT_EXCEEDED));

        // when
        marketDataRefreshService.refreshSingleStockFromFanOut(OVERSEAS_CODE, null);

        // then
        verify(stockMasterService, never()).markPriceUnsupported(any());
    }

    @Test
    @DisplayName("[가격이 갱신됐고 스코어 산출일이 그보다 이전이면 단건 스코어 재계산을 호출한다]")
    void refreshSingleStockFromFanOut_domesticNeedsScoreRefresh_recalculatesScore() {
        // given
        Stock domestic = Stock.of(DOMESTIC_CODE, "삼성전자", MarketType.KOSPI, ListingStatus.LISTED, "전기전자");
        given(stockMasterService.getStockByCode(DOMESTIC_CODE)).willReturn(domestic);
        LocalDate today = LocalDate.now();
        given(priceGapFillService.fillDomesticGap(DOMESTIC_CODE))
            .willReturn(PriceGapFillService.GapFillOutcome.apiSkipped(today));

        // when
        marketDataRefreshService.refreshSingleStockFromFanOut(DOMESTIC_CODE, today.minusDays(1));

        // then
        verify(scoreService).recalculateDomesticScore(DOMESTIC_CODE);
    }

    @Test
    @DisplayName("[스코어가 이미 최신이면 재계산을 호출하지 않는다]")
    void refreshSingleStockFromFanOut_domesticAlreadyFresh_skipsScoreRecalculation() {
        // given
        Stock domestic = Stock.of(DOMESTIC_CODE, "삼성전자", MarketType.KOSPI, ListingStatus.LISTED, "전기전자");
        given(stockMasterService.getStockByCode(DOMESTIC_CODE)).willReturn(domestic);
        LocalDate today = LocalDate.now();
        given(priceGapFillService.fillDomesticGap(DOMESTIC_CODE))
            .willReturn(PriceGapFillService.GapFillOutcome.apiSkipped(today));

        // when
        marketDataRefreshService.refreshSingleStockFromFanOut(DOMESTIC_CODE, today);

        // then
        verify(scoreService, never()).recalculateDomesticScore(any());
    }

    @Test
    @DisplayName("[가격이 갱신됐고 스코어 산출일이 그보다 이전이면 해외 단건 스코어 재계산을 호출한다]")
    void refreshSingleStockFromFanOut_overseasNeedsScoreRefresh_recalculatesScore() {
        // given
        Stock overseas = Stock.of(OVERSEAS_CODE, "APPLE INC", MarketType.NASDAQ, ListingStatus.LISTED, "720");
        given(stockMasterService.getStockByCode(OVERSEAS_CODE)).willReturn(overseas);
        LocalDate today = LocalDate.now();
        given(priceGapFillService.fillOverseasGap(OVERSEAS_CODE))
            .willReturn(PriceGapFillService.GapFillOutcome.apiSkipped(today));

        // when
        marketDataRefreshService.refreshSingleStockFromFanOut(OVERSEAS_CODE, today.minusDays(1));

        // then
        verify(scoreService).recalculateOverseasScore(OVERSEAS_CODE);
    }

    @Test
    @DisplayName("[단건 갱신(refreshStock)은 종목의 시장 구분에 따라 국내/해외 경로 중 하나만 타고, "
        + "그 자리에서 바로 배치 단위 후속작업(유동성/정규화)까지 수행한다]")
    void refreshStock_domesticStock_usesDomesticPathAndFinishesBatchImmediately() {
        // given
        Stock domestic = Stock.of(DOMESTIC_CODE, "삼성전자", MarketType.KOSPI, ListingStatus.LISTED, "전기전자");
        given(stockMasterService.getStockByCode(DOMESTIC_CODE)).willReturn(domestic);
        given(priceGapFillService.fillDomesticGap(DOMESTIC_CODE))
            .willReturn(PriceGapFillService.GapFillOutcome.apiSkipped(LocalDate.now()));

        // when
        marketDataRefreshService.refreshStock(DOMESTIC_CODE);

        // then
        verify(priceGapFillService).fillDomesticGap(DOMESTIC_CODE);
        verify(priceGapFillService, never()).fillOverseasGap(any());
        verify(stockLiquidityService).refreshDomestic(any());
        verify(scoreService).normalizeCrossSection(PeerGroup.DOMESTIC);
        verify(stockLiquidityService, never()).refreshOverseas(any());
    }

    @Test
    @DisplayName("[refreshAllExclusively는 락을 잡은 채로 refreshAll()을 실행한다]")
    void refreshAllExclusively_runsRefreshAllInsideLock() {
        // given
        stubLockToRunTask();
        given(stockMasterService.getAllListedStocks()).willReturn(List.of());

        // when
        Optional<Boolean> result = marketDataRefreshService.refreshAllExclusively();

        // then
        org.assertj.core.api.Assertions.assertThat(result).contains(Boolean.TRUE);
        verify(domesticStockMasterSyncService).syncStockMaster();
        verify(overseasStockMasterSyncService).syncAll();
    }

    @Test
    @DisplayName("[refreshAllExclusively는 이미 다른 실행이 락을 쥐고 있으면 refreshAll()을 실행하지 않는다 - "
        + "OhlcvCollectorScheduler(16:00)와 StartupCatchUpRunner(기동 시)가 겹칠 때 동시 실행을 막는다]")
    void refreshAllExclusively_lockHeld_doesNotRunRefreshAll() {
        // given
        given(redisLockService.runExclusively(any(), any(), any())).willReturn(Optional.empty());

        // when
        Optional<Boolean> result = marketDataRefreshService.refreshAllExclusively();

        // then
        org.assertj.core.api.Assertions.assertThat(result).isEmpty();
        verify(domesticStockMasterSyncService, never()).syncStockMaster();
        verify(stockMasterService, never()).getAllListedStocks();
    }
}
