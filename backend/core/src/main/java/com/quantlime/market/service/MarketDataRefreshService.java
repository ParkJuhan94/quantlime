package com.quantlime.market.service;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.common.util.SafeExecutor;
import com.quantlime.market.event.PriceRefreshRequestedEvent;
import com.quantlime.market.event.ScoreBatchCompletedEvent;
import com.quantlime.market.implement.PriceRefreshFailureHandler;
import com.quantlime.price.implement.DailyPriceReader;
import com.quantlime.price.service.PriceGapFillService;
import com.quantlime.price.service.StockLiquidityService;
import com.quantlime.score.cache.ScoreRankingCacheStore;
import com.quantlime.score.domain.PeerGroup;
import com.quantlime.score.implement.ScoreReader;
import com.quantlime.score.service.ScoreService;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.DomesticStockMasterSyncService;
import com.quantlime.stock.service.OverseasStockMasterSyncService;
import com.quantlime.stock.service.StockMasterService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 트리거1(운영 갱신) - 전체 상장종목(국내+해외)의 가격+스코어를 "마지막
 * 저장일 다음날부터 오늘까지"만 gap-fill한다({@link PriceGapFillService} 참고).
 * 종목별 실제 작업은 Kafka로 fan-out한다(2026-09-24, 카프카 다도메인 확장
 * Phase 1) - 순차 루프+150ms sleep 대신 종목별 이벤트를 발행하고, 실패한
 * 종목만 {@code @RetryableTopic}/DLT로 격리한다. 종목마스터 동기화(신규상장/
 * 상장폐지, 해외 한글명 백필)도 같은 트리거에 편입돼 있다(2026-08-01 - 별도
 * 주 1회 스케줄러 폐지).
 *
 * <p>이 서비스 하나가 dev 수동 트리거(/dev/refresh), 매일 16:00 배치
 * (OhlcvCollectorScheduler), 로컬 백엔드 기동 시 자동 캐치업
 * (StartupCatchUpRunner) 3곳에서 재사용된다.
 *
 * <p><b>fan-out과 락(LOCK_TTL)의 관계</b>: {@link #refreshAll()}은 종목별
 * 이벤트를 발행한 뒤 {@link PriceRefreshBatchGate}로 그 전부가 처리될 때까지
 * 블로킹 대기한 다음에야 반환한다 - 발행만 하고 곧바로 반환하면
 * {@link #refreshAllExclusively()}가 쥔 락이 실제 처리가 끝나기 훨씬 전에
 * 풀려버려, 동시 실행 방지라는 락의 목적 자체가 무너진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataRefreshService {

    // 종목 간 API 호출 딜레이(기존 OhlcvCollectorScheduler/
    // OverseasUniverseSelectionService와 동일한 값) - fan-out 컨슈머 리스너의
    // 동시성을 1로 고정한 채 이 딜레이를 그대로 유지해, 병렬화 이전과 동등한
    // Toss 호출 처리율을 보존한다(PriceRefreshConsumer 클래스 주석 참고).
    private static final long INTER_STOCK_DELAY_MS = 150;
    private static final String LOCK_KEY = "lock:market-data-refresh";
    // 전종목 갭필 소요시간 재산정(2026-10-01, Kafka 점검): 갱신 대상은 약 8,100종목(국내
    // 2,598 + 해외 5,504, 로컬 DB 기준)이고 컨슈머 1개가 순차 처리한다. 국내 실측 처리
    // 속도가 분당 약 120종목이라(docs/CHANGELOG.md 2026-09-29/30) 해외도 비슷하다면
    // 전체가 약 68분 - 기존 60분 TTL은 매일 구조적으로 부족했다(해외 속도는 미측정).
    // TTL이 소요시간보다 짧으면 락이 만료돼 다른 트리거가 끼어들어 이 락의 목적(동시 실행
    // 방지)이 무의미해진다. 크래시 시 락이 TTL만큼 남아 재기동 캐치업이 그동안 스킵되는
    // 비용과의 절충으로 120분.
    private static final Duration LOCK_TTL = Duration.ofMinutes(120);
    // fan-out 완료 대기 상한(국내+해외가 하나의 데드라인을 공유) - 대기가 끝난 뒤에도
    // 유동성/정규화/지수 갭필이 이어지므로 LOCK_TTL보다 충분히 짧게 둔다.
    private static final Duration BATCH_AWAIT_TIMEOUT = Duration.ofMinutes(100);
    // 갱신 순서 결정용 거래대금 조회 기간 - DomesticUniverseSelectionService의
    // 백테스트 유니버스 선정("최근 3개월")과 같은 값을 재사용한다.
    private static final long TRADING_VALUE_LOOKBACK_MONTHS = 3;
    // 유동성 스냅샷("최근 20거래일") 조회 기간 - 달력일 기준 근사치. 주말+
    // 공휴일을 감안해 20거래일을 넉넉히 커버하도록 여유를 둔다(정밀한
    // 거래일 캘린더 조인 없이 단순 날짜 뺄셈으로 충분한 용도).
    private static final long LIQUIDITY_LOOKBACK_CALENDAR_DAYS = 28;

    private final StockMasterService stockMasterService;
    private final DomesticStockMasterSyncService domesticStockMasterSyncService;
    private final OverseasStockMasterSyncService overseasStockMasterSyncService;
    private final DailyPriceReader dailyPriceReader;
    private final ScoreReader scoreReader;
    private final PriceRefreshFailureHandler priceRefreshFailureHandler;
    private final PriceGapFillService priceGapFillService;
    private final StockLiquidityService stockLiquidityService;
    private final ScoreService scoreService;
    private final ScoreRankingCacheStore scoreRankingCacheStore;
    private final BenchmarkIndexBackfillService benchmarkIndexBackfillService;
    private final InvestorTradingBackfillService investorTradingBackfillService;
    private final RedisLockService redisLockService;
    private final PriceRefreshBatchGate priceRefreshBatchGate;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 락을 잡은 채로만 {@link #refreshAll()}을 실행한다 - OhlcvCollectorScheduler
     * (매일 16:00)와 StartupCatchUpRunner(기동 시)가 각자 다른 스레드에서
     * 이 트리거를 부르는데, 서버가 하필 16:00 근처에 재기동되면 락 없이는
     * 둘이 동시에 refreshAll()을 시작할 수 있었다(2026-08-02 검토 중 발견).
     * 다운스트림 쓰기(gap-fill/스코어 재계산/벤치마크 갱신)는 대부분
     * "이미 최신이면 스킵" 방식이라 두 번 돌아도 데이터가 깨지진 않지만,
     * 외부 API 호출이 그대로 두 배로 나가 레이트리밋 예산을 낭비한다.
     * videofeed 모듈의 runXxxExclusively 패턴({@link
     * com.quantlime.videofeed.service.FeedCollectionFacade} 등)을 그대로
     * 따른다 - 락을 이미 다른 실행이 쥐고 있으면 refreshAll()을 실행하지
     * 않고 빈 Optional을 반환한다.
     */
    public Optional<Boolean> refreshAllExclusively() {
        return redisLockService.runExclusively(LOCK_KEY, LOCK_TTL, () -> {
            refreshAll();
            return Boolean.TRUE;
        });
    }

    public void refreshAll() {
        // 종목마스터 동기화(신규상장/상장폐지, 해외 한글명 백필 등)를 이
        // 트리거에 편입한다(2026-08-01, 사용자 요청) - 기존엔 별도 주 1회
        // 스케줄러(StockMasterSyncScheduler/OverseasStockMasterSyncScheduler,
        // 일요일 새벽)만 있어 신규 상장 종목이 최대 일주일 뒤처져야 가격/
        // 스코어 갱신 대상에 들어왔다. 아래 getAllListedStocks() 조회보다
        // 먼저 실행해야 이번 실행에서 새로 등록된 종목까지 곧바로 반영된다.
        // 실패해도 전종목 가격 갱신 자체는 막지 않는다(다른 백필과 동일 패턴).
        SafeExecutor.runSafely("국내 종목마스터 동기화", domesticStockMasterSyncService::syncStockMaster);
        SafeExecutor.runSafely("해외 종목마스터 동기화", overseasStockMasterSyncService::syncAll);

        // 가격 소스가 커버하지 않는 것으로 이미 표시된 종목(price_unsupported)은
        // 제외한다 - 매 기동마다 같은 stock-not-found(404)를 반복하지 않기 위함.
        List<Stock> stocks = stockMasterService.getAllListedStocks();
        LocalDate tradingValueSince = LocalDate.now().minusMonths(TRADING_VALUE_LOOKBACK_MONTHS);
        List<Stock> domestic = orderByTradingValueDesc(
            stocks.stream()
                .filter(stock -> stock.getMarketType().isDomestic())
                .filter(stock -> !stock.isPriceUnsupported())
                .toList(),
            dailyPriceReader.findDomesticCodesByTradingValueDesc(tradingValueSince));
        List<Stock> overseas = orderByTradingValueDesc(
            stocks.stream()
                .filter(stock -> !stock.getMarketType().isDomestic())
                .filter(stock -> !stock.isPriceUnsupported())
                .toList(),
            dailyPriceReader.findOverseasCodesByTradingValueDesc(tradingValueSince));

        // 종목별 "스코어 재계산 필요 여부" 판단에 쓰는 최신 스코어 산출일을
        // 발행 전 배치로 한 번만 가져와 각 이벤트에 실어 보낸다(2026-09
        // 성능 감사) - 컨슈머가 종목마다 다시 조회하면 국내+해외 약 9,000회
        // 개별 왕복이 재발한다.
        Map<String, LocalDate> latestScoreDateByStockCode = scoreReader.findLatestScoreDateByStockCode();

        String runId = UUID.randomUUID().toString();
        priceRefreshBatchGate.startBatch(runId, PeerGroup.DOMESTIC, domestic.size());
        priceRefreshBatchGate.startBatch(runId, PeerGroup.OVERSEAS, overseas.size());
        domestic.forEach(stock -> eventPublisher.publishEvent(new PriceRefreshRequestedEvent(
            runId, stock.getStockCode(), PeerGroup.DOMESTIC, latestScoreDateByStockCode.get(stock.getStockCode()))));
        overseas.forEach(stock -> eventPublisher.publishEvent(new PriceRefreshRequestedEvent(
            runId, stock.getStockCode(), PeerGroup.OVERSEAS, latestScoreDateByStockCode.get(stock.getStockCode()))));
        log.info("전종목 가격+스코어 갱신 fan-out 발행: 국내={}종목, 해외={}종목, runId={}",
            domestic.size(), overseas.size(), runId);

        // 종목별 컨슈머가 전부 끝날 때까지 대기한 뒤에야 배치 단위
        // 후속작업(유동성 스냅샷/횡단면 정규화)을 정확히 1회 수행한다 -
        // 클래스 주석의 "fan-out과 락의 관계" 참고.
        // 국내·해외가 같은 컨슈머 하나로 순차 소비되므로 대기 상한도 하나의 데드라인을
        // 공유한다 - 각각 BATCH_AWAIT_TIMEOUT을 쓰면 최대 2배(락 TTL 초과)까지 늘어난다.
        Instant awaitDeadline = Instant.now().plus(BATCH_AWAIT_TIMEOUT);
        boolean domesticDone = priceRefreshBatchGate.awaitCompletion(
            runId, PeerGroup.DOMESTIC, remainingUntil(awaitDeadline));
        boolean overseasDone = priceRefreshBatchGate.awaitCompletion(
            runId, PeerGroup.OVERSEAS, remainingUntil(awaitDeadline));
        if (!domesticDone) {
            log.warn("국내 가격 갱신 fan-out 배치 대기 시간 초과(일부 종목 미완료로 추정): runId={}", runId);
        }
        if (!overseasDone) {
            log.warn("해외 가격 갱신 fan-out 배치 대기 시간 초과(일부 종목 미완료로 추정): runId={}", runId);
        }

        // 국내·해외 횡단면 정규화(finishDomesticBatch/finishOverseasBatch 내부의
        // scoreService.normalizeCrossSection)가 이 시점에 둘 다 끝나 있다 -
        // /api/dashboard/scores(watchlistOnly=false) 랭킹 캐시(ScoreRankingCacheStore)를
        // 여기서 무효화해야 다음 요청이 이번에 갱신된 최신 스코어로 다시
        // 캐싱한다(2026-09 성능 감사). 개별 peer group이 끝날 때마다 지우면
        // "국내만 끝난 상태"에서 scope=all/overseas 키가 갱신 전 상태로 남는
        // 애매한 창이 생겨, 둘 다 끝난 뒤 한 번에 지운다.
        finishDomesticBatch();
        finishOverseasBatch();
        SafeExecutor.runSafely("스코어 랭킹 캐시 무효화", scoreRankingCacheStore::evictAll);
        // 스코어가 확정된 이 시점에 후속 작업(사분면 변화 알림 등)을 깨운다 -
        // 구독자(notification)를 market이 직접 알지 않도록 도메인 이벤트로만 알린다.
        SafeExecutor.runSafely("스코어 배치 완료 이벤트 발행",
            () -> eventPublisher.publishEvent(new ScoreBatchCompletedEvent()));

        // 국내 지수(코스피/코스닥) 일봉 갭필 + 투자자별 매매대금(주/월)을 같은
        // 트리거에 편입한다(2026-07-29, 사용자 요청) - MarketIndexCache의 지수
        // 등락률 자체계산(전일 종가 기준)이 이 데이터에 의존하게 되면서 매일
        // 갱신될 필요가 생겼다. backfillAllIfNeeded()(딥백필, 백테스트 데이터셋
        // 준비 트리거 전용)가 아니라 refreshRecentIfNeeded()를 쓴다 - 딥백필은
        // "이미 400일치가 있으면 스킵"이라 여기 물려두면 최신 종가가 영원히
        // 안 갱신되는 버그가 있었다(2026-07-30 실제 발견 - 이 스킵 로직 때문에
        // KOSPI 벤치마크가 2주 전 날짜에 멈춰 등락률이 완전히 틀어졌었음).
        // 실패해도 전종목 가격 갱신 자체는 막지 않는다.
        SafeExecutor.runSafely("국내 지수 벤치마크 최신 갭필", benchmarkIndexBackfillService::refreshRecentIfNeeded);
        SafeExecutor.runSafely("투자자별 매매대금 갱신", investorTradingBackfillService::refreshAllIfNeeded);

        log.info("전종목 가격+스코어 갱신 완료: 국내={}종목, 해외={}종목", domestic.size(), overseas.size());
    }

    public void refreshStock(String stockCode) {
        Stock stock = stockMasterService.getStockByCode(stockCode);
        // 단건 갱신(관심종목 등록 트리거)이라 배치 프리페치의 이점은 없지만,
        // refreshSingleStock의 시그니처를 하나로 유지하려고 동일하게 맵을
        // 조회해 넘긴다 - 이 경로는 고빈도가 아니라 이 여분의 조회(~10ms)
        // 비용이 무시할 만하다.
        LocalDate latestScoreDate = scoreReader.findLatestScoreDateByStockCode().get(stockCode);
        PeerGroup peerGroup = refreshSingleStock(stock, latestScoreDate);
        if (peerGroup == PeerGroup.DOMESTIC) {
            finishDomesticBatch();
        } else {
            finishOverseasBatch();
        }
    }

    /**
     * Kafka fan-out 컨슈머(2026-09-24, {@code PriceRefreshConsumer}) 전용
     * 진입점 - 종목 하나의 가격 gap-fill + (필요 시) 스코어 재계산만 수행하고
     * 반환한다. 유동성 스냅샷/횡단면 정규화 같은 배치 전체 단위 후속작업은
     * 절대 여기서 하지 않는다 - {@link #refreshDomesticStock}/{@link
     * #refreshOverseasStock}이 원래 루프 안에서 하던 일과 달리, 이 메서드는
     * 종목마다(9,000번) 독립적으로 호출되므로 배치 단위 작업을 여기 넣으면
     * 정규화가 9,000번 도는 성능 재앙이 된다({@link #refreshAll()} 클래스
     * 주석 참고 - 배치 단위 후속작업은 오직 refreshAll()의 fan-in 대기 이후
     * 정확히 1회만 수행한다).
     *
     * @param latestScoreDate {@code refreshAll()}이 발행 시점에 이미 조회해
     *     둔 배치 스냅샷 값(nullable) - 여기서 다시 종목별로 조회하지 않는다
     *     (2026-09 성능 감사와 동일한 이유).
     * @return 이 종목이 속한 모집단 - 호출부(컨슈머)가 어느 카운터를
     *     감소시켜야 할지 판단하는 데 쓴다.
     */
    public PeerGroup refreshSingleStockFromFanOut(String stockCode, LocalDate latestScoreDate) {
        Stock stock = stockMasterService.getStockByCode(stockCode);
        return refreshSingleStock(stock, latestScoreDate);
    }

    private PeerGroup refreshSingleStock(Stock stock, LocalDate latestScoreDate) {
        String stockCode = stock.getStockCode();
        if (stock.getMarketType().isDomestic()) {
            StockRefreshOutcome outcome = refreshDomesticStock(stock, latestScoreDate);
            if (outcome.needsScoreRefresh()) {
                scoreService.recalculateDomesticScore(stockCode);
            }
            return PeerGroup.DOMESTIC;
        }
        StockRefreshOutcome outcome = refreshOverseasStock(stock, latestScoreDate);
        if (outcome.needsScoreRefresh()) {
            scoreService.recalculateOverseasScore(stockCode);
        }
        return PeerGroup.OVERSEAS;
    }

    /**
     * 국내 종목 하나의 가격 gap-fill을 수행하고 스코어 재계산이 필요한지
     * 판단만 한다 - 실제 재계산 호출과 배치 단위 후속작업은 호출부 책임이다
     * (순차 배치는 {@link #finishDomesticBatch}에서 한 번에, fan-out은
     * {@link #refreshSingleStockFromFanOut}이 종목별로 즉시).
     */
    private StockRefreshOutcome refreshDomesticStock(Stock stock, LocalDate latestScoreDate) {
        String stockCode = stock.getStockCode();
        Optional<LocalDate> latestPriceDate = Optional.empty();
        try {
            PriceGapFillService.GapFillOutcome outcome = priceGapFillService.fillDomesticGap(stockCode);
            latestPriceDate = outcome.calledApi()
                ? dailyPriceReader.findLatestDomesticTradeDate(stockCode)
                : Optional.ofNullable(outcome.latestTradeDate());
            if (outcome.calledApi()) {
                sleepBetweenStocks();
            }
        } catch (Exception e) {
            priceRefreshFailureHandler.handleDomestic(stockCode, e);
            latestPriceDate = dailyPriceReader.findLatestDomesticTradeDate(stockCode);
        }
        return new StockRefreshOutcome(needsScoreRefresh(latestPriceDate, latestScoreDate));
    }

    /** 해외 버전 - {@link #refreshDomesticStock}과 대칭. */
    private StockRefreshOutcome refreshOverseasStock(Stock stock, LocalDate latestScoreDate) {
        String stockCode = stock.getStockCode();
        Optional<LocalDate> latestPriceDate = Optional.empty();
        try {
            PriceGapFillService.GapFillOutcome outcome = priceGapFillService.fillOverseasGap(stockCode);
            latestPriceDate = outcome.calledApi()
                ? dailyPriceReader.findLatestOverseasTradeDate(stockCode)
                : Optional.ofNullable(outcome.latestTradeDate());
            if (outcome.calledApi()) {
                sleepBetweenStocks();
            }
        } catch (Exception e) {
            priceRefreshFailureHandler.handleOverseas(stockCode, e);
            latestPriceDate = dailyPriceReader.findLatestOverseasTradeDate(stockCode);
        }
        return new StockRefreshOutcome(needsScoreRefresh(latestPriceDate, latestScoreDate));
    }

    /** 국내 배치 단위 후속작업 - 정확히 1회만 호출돼야 한다(클래스 주석 참고). */
    private void finishDomesticBatch() {
        // 유동성 스냅샷은 스코어 재계산보다 먼저 갱신한다 - 횡단면 정규화
        // 모집단(잡주 제외) 결정에 쓰이므로 그 전 단계에서 최신이어야 한다.
        // 실패해도 가격/스코어 갱신 자체는 막지 않는다(다른 백필과 동일 패턴).
        SafeExecutor.runSafely("국내 유동성 스냅샷 갱신",
            () -> stockLiquidityService.refreshDomestic(liquidityLookbackSince()));
        SafeExecutor.runSafely("국내 스코어 횡단면 정규화",
            () -> scoreService.normalizeCrossSection(PeerGroup.DOMESTIC));
    }

    /** 해외 배치 단위 후속작업 - {@link #finishDomesticBatch}와 대칭. */
    private void finishOverseasBatch() {
        SafeExecutor.runSafely("해외 유동성 스냅샷 갱신",
            () -> stockLiquidityService.refreshOverseas(liquidityLookbackSince()));
        SafeExecutor.runSafely("해외 스코어 횡단면 정규화",
            () -> scoreService.normalizeCrossSection(PeerGroup.OVERSEAS));
    }

    private static Duration remainingUntil(Instant deadline) {
        Duration remaining = Duration.between(Instant.now(), deadline);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private void sleepBetweenStocks() {
        try {
            Thread.sleep(INTER_STOCK_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("가격 갱신 중단: 인터럽트 발생");
        }
    }

    /**
     * 스코어의 최신 산출일이 가격의 최신 저장일보다 이미 앞서 있지 않으면
     * (=아직 최신 가격까지 반영 못했으면) 재계산 대상에 포함한다. 가격
     * gap-fill과 달리 스코어 계산 자체는 외부 레이트리밋 대상이 아니지만,
     * 이미 최신인 종목까지 매번 청크에 실어 퀀트 엔진을 부르는 왕복을
     * 아끼기 위한 최소한의 필터다.
     *
     * <p>{@code latestScoreDate}는 배치 시작 시점에 한 번만 조회한 스냅샷
     * ({@link ScoreReader#findLatestScoreDateByStockCode})에서 이
     * 종목분만 이미 뽑아 전달받은 값이다 - 이전엔 종목마다 개별 쿼리를
     * 날렸다(2026-09 성능 감사).
     */
    private boolean needsScoreRefresh(Optional<LocalDate> latestPriceDate, LocalDate latestScoreDate) {
        if (latestPriceDate.isEmpty()) {
            return false;
        }
        return latestScoreDate == null || latestScoreDate.isBefore(latestPriceDate.get());
    }

    /**
     * {@code stocks}를 {@code rankedCodesDesc}(거래대금 상위 순 종목코드)의
     * 순서로 정렬한다 - 갱신 도중 중단돼도(리소스 부족·재기동 등) 실사용
     * 비중이 큰 종목이 먼저 처리되게 하기 위함(2026-09 감사 세션).
     *
     * <p>이전엔 {@code getAllListedStocks()}가 반환하는 순서(사실상
     * {@code idx_stock_listing_status_market_type} 인덱스 스캔 순서, 시장구분
     * enum명 오름차순인 KONEX→KOSDAQ→KOSPI)를 그대로 썼다 - 이 순서에는
     * 아무 의도가 없었는데도 실행이 중간에 끊기면 항상 KOSPI가 가장 먼저
     * 희생됐다(실측 - KOSPI 스코어가 몇 주간 갱신 안 됨). 거래대금 순위에
     * 없는 종목(신규상장 등 최근 {@link #TRADING_VALUE_LOOKBACK_MONTHS}개월
     * 거래 이력이 없는 경우)은 원래 상대 순서를 유지한 채 맨 뒤로 보낸다
     * (안정 정렬 - {@link List#sort}는 TimSort라 동순위끼리 순서가 안 바뀐다).
     */
    private LocalDate liquidityLookbackSince() {
        return LocalDate.now().minusDays(LIQUIDITY_LOOKBACK_CALENDAR_DAYS);
    }

    private List<Stock> orderByTradingValueDesc(List<Stock> stocks, List<String> rankedCodesDesc) {
        Map<String, Integer> rankByCode = new HashMap<>();
        for (int i = 0; i < rankedCodesDesc.size(); i++) {
            rankByCode.put(rankedCodesDesc.get(i), i);
        }
        List<Stock> ordered = new ArrayList<>(stocks);
        ordered.sort(Comparator.comparingInt(
            stock -> rankByCode.getOrDefault(stock.getStockCode(), Integer.MAX_VALUE)));
        return ordered;
    }

    private record StockRefreshOutcome(boolean needsScoreRefresh) {
    }
}
