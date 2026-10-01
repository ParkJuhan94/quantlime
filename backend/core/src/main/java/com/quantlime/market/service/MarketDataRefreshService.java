package com.quantlime.market.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.lock.RedisLockService;
import com.quantlime.common.util.SafeExecutor;
import com.quantlime.market.event.PriceRefreshRequestedEvent;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.price.service.PriceGapFillService;
import com.quantlime.price.service.StockLiquidityService;
import com.quantlime.score.cache.ScoreRankingCacheStore;
import com.quantlime.score.domain.PeerGroup;
import com.quantlime.score.repository.ScoreRepository;
import com.quantlime.score.service.ScoreService;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.DomesticStockMasterSyncService;
import com.quantlime.stock.service.OverseasStockMasterSyncService;
import com.quantlime.stock.service.StockMasterService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

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
    private final DomesticDailyPriceRepository domesticDailyPriceRepository;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;
    private final ScoreRepository scoreRepository;
    private final PriceGapFillService priceGapFillService;
    private final StockLiquidityService stockLiquidityService;
    private final ScoreService scoreService;
    private final ScoreRankingCacheStore scoreRankingCacheStore;
    private final BenchmarkIndexBackfillService benchmarkIndexBackfillService;
    private final InvestorTradingBackfillService investorTradingBackfillService;
    private final RedisLockService redisLockService;
    private final PriceRefreshBatchGate priceRefreshBatchGate;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

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
            domesticDailyPriceRepository.findStockCodesOrderedByTradingValueDesc(tradingValueSince));
        List<Stock> overseas = orderByTradingValueDesc(
            stocks.stream()
                .filter(stock -> !stock.getMarketType().isDomestic())
                .filter(stock -> !stock.isPriceUnsupported())
                .toList(),
            overseasDailyPriceRepository.findStockCodesOrderedByTradingValueDesc(tradingValueSince));

        // 종목별 "스코어 재계산 필요 여부" 판단에 쓰는 최신 스코어 산출일을
        // 발행 전 배치로 한 번만 가져와 각 이벤트에 실어 보낸다(2026-09
        // 성능 감사) - 컨슈머가 종목마다 다시 조회하면 국내+해외 약 9,000회
        // 개별 왕복이 재발한다.
        Map<String, LocalDate> latestScoreDateByStockCode = scoreRepository.findLatestScoreDateByStockCode();

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
        LocalDate latestScoreDate = scoreRepository.findLatestScoreDateByStockCode().get(stockCode);
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
            StockRefreshOutcome outcome = refreshDomesticStock(stock, latestScoreDate, new AtomicInteger());
            if (outcome.needsScoreRefresh()) {
                scoreService.recalculateDomesticScore(stockCode);
            }
            return PeerGroup.DOMESTIC;
        }
        StockRefreshOutcome outcome = refreshOverseasStock(stock, latestScoreDate, new AtomicInteger());
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
    private StockRefreshOutcome refreshDomesticStock(
        Stock stock, LocalDate latestScoreDate, AtomicInteger failures) {
        String stockCode = stock.getStockCode();
        Optional<LocalDate> latestPriceDate = Optional.empty();
        try {
            PriceGapFillService.GapFillOutcome outcome = priceGapFillService.fillDomesticGap(stockCode);
            latestPriceDate = outcome.calledApi()
                ? latestPriceDate(stockCode)
                : Optional.ofNullable(outcome.latestTradeDate());
            if (outcome.calledApi()) {
                sleepBetweenStocks();
            }
        } catch (Exception e) {
            handleDomesticFailure(stockCode, e, failures);
            latestPriceDate = latestPriceDate(stockCode);
        }
        return new StockRefreshOutcome(needsScoreRefresh(latestPriceDate, latestScoreDate));
    }

    /** 해외 버전 - {@link #refreshDomesticStock}과 대칭. */
    private StockRefreshOutcome refreshOverseasStock(
        Stock stock, LocalDate latestScoreDate, AtomicInteger failures) {
        String stockCode = stock.getStockCode();
        Optional<LocalDate> latestPriceDate = Optional.empty();
        try {
            PriceGapFillService.GapFillOutcome outcome = priceGapFillService.fillOverseasGap(stockCode);
            latestPriceDate = outcome.calledApi()
                ? latestOverseasPriceDate(stockCode)
                : Optional.ofNullable(outcome.latestTradeDate());
            if (outcome.calledApi()) {
                sleepBetweenStocks();
            }
        } catch (Exception e) {
            handleOverseasFailure(stockCode, e, failures);
            latestPriceDate = latestOverseasPriceDate(stockCode);
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

    /**
     * 국내 가격 갱신 실패를 처리한다. Toss 캔들 API가 커버하지 않는 종목
     * (KONEX·스팩·상폐 잔존 등)은 조회 시 stock-not-found(404)만 반복하므로,
     * 이 경우 해당 종목을 '가격 미커버'로 표시해 이후 기동의 갭필 및 랭킹
     * 스윕(DomesticListedStockCache) 대상에서 제외한다 - 매 기동 404 폭주와
     * 불필요한 Toss 쿼터 소모를 근본 차단한다. 그 외 실패(레이트리밋·일시
     * 장애 등)는 다음 기동에 재시도해야 하므로 표시하지 않고 에러 로그만 남긴다.
     */
    private void handleDomesticFailure(String stockCode, Exception e, AtomicInteger failures) {
        if (isStockNotFound(e)) {
            stockMasterService.markPriceUnsupported(stockCode);
            log.info("Toss 미커버 종목(stock-not-found)으로 표시, 이후 갭필/스윕에서 제외: stockCode={}", stockCode);
            return;
        }
        failures.incrementAndGet();
        recordPriceRefreshFailure(PeerGroup.DOMESTIC);
        // 레이트리밋/일시 장애 등으로 다수 종목이 한꺼번에 실패하면(예: Toss
        // 장애) 종목마다 풀 스택트레이스를 찍는 게 콘솔을 뒤덮어 정작 원인
        // 파악을 방해한다. 원인 자체(스택트레이스)가 필요하면 재현 후
        // debug 레벨로 임시 확인할 것. 이 실패는 여기서 삼켜지므로 컨슈머는 성공으로
        // 처리하고 @RetryableTopic/DLT(dlt_messages_total)에도 잡히지 않는다 - 규모는
        // market.price.refresh.failures 카운터로 본다(2026-10-01, fan-out에서는 호출마다
        // 새 AtomicInteger를 써서 배치 전체 실패 수를 더는 알 수 없었다).
        log.warn("국내 가격 갱신 실패(해당 종목만 스킵): stockCode={}, error={}", stockCode, e.getMessage());
        log.debug("국내 가격 갱신 실패 상세: stockCode={}", stockCode, e);
    }

    /**
     * 해외 가격 갱신 실패를 처리한다. 국내(handleDomesticFailure)와 대칭 -
     * 해외도 이제 같은 Toss 캔들 API를 쓰므로(2026-07-29, KIS에서 이관)
     * stock-not-found 판별 로직을 그대로 공유한다. 이 안전장치가 없던 이전
     * 버전에서는 KIS 전용 마스터에만 있고 실제로는 조회 불가능한 종목이
     * 매 스윕마다 계속 실패하면서도 영원히 제외되지 않아, 레이트리밋 예산을
     * 갉아먹으며 다른 정상 종목의 산발적 실패(레이트리밋)를 유발하는 원인
     * 중 하나였다.
     *
     * <p>해외는 여기에 더해 {@code isUnsupportedSymbolFormat}도 함께 본다 -
     * KIS 해외주식 마스터파일에서 유래한 종목코드 중 "AAC/UN"·"ABR/F"처럼
     * "/"가 섞인 SPAC 유닛/우선주 표기가 있는데(길이 6자 제한만으로는 안
     * 걸러짐, OverseasStockMasterSyncService 참고), Toss 심볼 파라미터는
     * `^[A-Za-z0-9.,\-]+$`만 허용해 이런 종목은 항상 404가 아니라 400으로
     * 거부된다(실측 - 2026-07-30). 404만 보던 기존 체크로는 이 400이
     * 잡히지 않아 매 기동 무한 반복 실패의 원인이 됐다.
     */
    private void handleOverseasFailure(String stockCode, Exception e, AtomicInteger failures) {
        if (isStockNotFound(e) || isUnsupportedSymbolFormat(e)) {
            stockMasterService.markPriceUnsupported(stockCode);
            log.info("Toss 미커버 해외종목(stock-not-found/invalid-symbol)으로 표시, 이후 갭필/스윕에서 제외: stockCode={}", stockCode);
            return;
        }
        failures.incrementAndGet();
        recordPriceRefreshFailure(PeerGroup.OVERSEAS);
        log.warn("해외 가격 갱신 실패(해당 종목만 스킵): stockCode={}, error={}", stockCode, e.getMessage());
        log.debug("해외 가격 갱신 실패 상세: stockCode={}", stockCode, e);
    }

    /**
     * Toss 캔들/현재가 API는 심볼이 자신의 문자 패턴(`^[A-Za-z0-9.,\-]+$`)을
     * 벗어나면 400 Bad Request로 거부한다 - 우리 쪽 요청 파라미터 자체는
     * 항상 올바르게 구성되므로(stockCode는 DB에 이미 저장된 값을 그대로
     * 전달), 이 400은 사실상 항상 "이 심볼은 Toss가 절대 못 받는다"는
     * 영구적 신호다(일시적 장애가 아님).
     */
    private boolean isUnsupportedSymbolFormat(Exception e) {
        return e instanceof ExternalApiException
            && e.getCause() instanceof HttpClientErrorException.BadRequest;
    }

    private boolean isStockNotFound(Exception e) {
        return e instanceof ExternalApiException
            && e.getCause() instanceof HttpClientErrorException.NotFound;
    }

    private void recordPriceRefreshFailure(PeerGroup peerGroup) {
        Counter.builder("market.price.refresh.failures")
            .tag("peerGroup", peerGroup.getWireValue())
            .description("가격 갱신 중 삼켜진(재시도/DLT로 가지 않는) 외부 API 실패 수")
            .register(meterRegistry)
            .increment();
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

    private Optional<LocalDate> latestPriceDate(String stockCode) {
        return domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(DomesticDailyPrice::getTradeDate);
    }

    private Optional<LocalDate> latestOverseasPriceDate(String stockCode) {
        return overseasDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(OverseasDailyPrice::getTradeDate);
    }

    /**
     * 스코어의 최신 산출일이 가격의 최신 저장일보다 이미 앞서 있지 않으면
     * (=아직 최신 가격까지 반영 못했으면) 재계산 대상에 포함한다. 가격
     * gap-fill과 달리 스코어 계산 자체는 외부 레이트리밋 대상이 아니지만,
     * 이미 최신인 종목까지 매번 청크에 실어 퀀트 엔진을 부르는 왕복을
     * 아끼기 위한 최소한의 필터다.
     *
     * <p>{@code latestScoreDate}는 배치 시작 시점에 한 번만 조회한 스냅샷
     * ({@link ScoreRepository#findLatestScoreDateByStockCode})에서 이
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
