package com.quantlime.market.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.lock.RedisLockService;
import com.quantlime.common.util.SafeExecutor;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.price.service.PriceGapFillService;
import com.quantlime.score.repository.ScoreRepository;
import com.quantlime.score.service.ScoreService;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.OverseasStockMasterSyncService;
import com.quantlime.stock.service.StockMasterService;
import com.quantlime.stock.service.StockMasterSyncService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * 트리거1(운영 갱신) - 전체 상장종목(국내+해외)의 가격+스코어를 "마지막
 * 저장일 다음날부터 오늘까지"만 gap-fill한다({@link PriceGapFillService} 참고).
 * 국내/해외 모두 이제 같은 Toss 캔들 API를 쓰지만(2026-07-29, 해외는 KIS에서
 * 이관), 한쪽이 느려져도 다른 쪽 갱신이 막히지 않도록 여전히 별도 스레드에서
 * 병렬 실행한다. 종목마스터 동기화(신규상장/상장폐지, 해외 한글명 백필)도
 * 같은 트리거에 편입돼 있다(2026-08-01 - 별도 주 1회 스케줄러 폐지).
 *
 * <p>이 서비스 하나가 dev 수동 트리거(/dev/refresh), 매일 16:00 배치
 * (OhlcvCollectorScheduler), 로컬 백엔드 기동 시 자동 캐치업
 * (StartupCatchUpRunner) 3곳에서 재사용된다 - 기존에는 이 셋이 각자
 * "고정 10일 재조회", "총 건수 기준 백필", "앵커종목 하나만 보고 판단"으로
 * 서로 다른 방식이었다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataRefreshService {

    // 종목 간 API 호출 딜레이(기존 OhlcvCollectorScheduler/
    // OverseasUniverseSelectionService와 동일한 값) - 실제로 외부 API를
    // 호출한 종목 다음에만 걸어, 이미 최신이라 스킵된 종목까지 매번 딜레이를
    // 물지 않는다(그래야 정상 상태에서 전종목 스윕이 빠르게 끝난다).
    private static final long INTER_STOCK_DELAY_MS = 150;
    private static final String LOCK_KEY = "lock:market-data-refresh";
    // 전종목(국내+해외 약 9천개) 갭필은 "수 분~수십 분"(DevController 참고)
    // 걸릴 수 있어 videofeed 락들(10~30분)보다 여유 있게 잡는다 - TTL이
    // 실제 소요시간보다 짧으면 락이 만료돼 다른 트리거가 끼어들어 이
    // 수정의 목적(동시 실행 방지) 자체가 무의미해진다.
    private static final Duration LOCK_TTL = Duration.ofMinutes(60);

    private final StockMasterService stockMasterService;
    private final StockMasterSyncService stockMasterSyncService;
    private final OverseasStockMasterSyncService overseasStockMasterSyncService;
    private final DomesticDailyPriceRepository domesticDailyPriceRepository;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;
    private final ScoreRepository scoreRepository;
    private final PriceGapFillService priceGapFillService;
    private final ScoreService scoreService;
    private final BenchmarkIndexBackfillService benchmarkIndexBackfillService;
    private final InvestorTradingBackfillService investorTradingBackfillService;
    private final RedisLockService redisLockService;
    private final TaskExecutor domesticMarketDataRefreshTaskExecutor;
    private final TaskExecutor overseasMarketDataRefreshTaskExecutor;

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
        SafeExecutor.runSafely("국내 종목마스터 동기화", stockMasterSyncService::syncStockMaster);
        SafeExecutor.runSafely("해외 종목마스터 동기화", overseasStockMasterSyncService::syncAll);

        // 가격 소스가 커버하지 않는 것으로 이미 표시된 종목(price_unsupported)은
        // 제외한다 - 매 기동마다 같은 stock-not-found(404)를 반복하지 않기 위함.
        List<Stock> stocks = stockMasterService.getAllListedStocks();
        List<Stock> domestic = stocks.stream()
            .filter(stock -> stock.getMarketType().isDomestic())
            .filter(stock -> !stock.isPriceUnsupported())
            .toList();
        List<Stock> overseas = stocks.stream()
            .filter(stock -> !stock.getMarketType().isDomestic())
            .filter(stock -> !stock.isPriceUnsupported())
            .toList();

        AtomicInteger domesticFailures = new AtomicInteger();
        AtomicInteger overseasFailures = new AtomicInteger();
        CompletableFuture<Void> domesticDone = CompletableFuture.runAsync(
            () -> refreshDomestic(domestic, domesticFailures), domesticMarketDataRefreshTaskExecutor);
        CompletableFuture<Void> overseasDone = CompletableFuture.runAsync(
            () -> refreshOverseas(overseas, overseasFailures), overseasMarketDataRefreshTaskExecutor);
        CompletableFuture.allOf(domesticDone, overseasDone).join();

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

        log.info("전종목 가격+스코어 갱신 완료: 국내={}종목(실패={}), 해외={}종목(실패={})",
            domestic.size(), domesticFailures.get(), overseas.size(), overseasFailures.get());
    }

    public void refreshStock(String stockCode) {
        Stock stock = stockMasterService.getStockByCode(stockCode);
        if (stock.getMarketType().isDomestic()) {
            refreshDomestic(List.of(stock), new AtomicInteger());
        } else {
            refreshOverseas(List.of(stock), new AtomicInteger());
        }
    }

    private void refreshDomestic(List<Stock> stocks, AtomicInteger failures) {
        List<String> needsScoreRefresh = new ArrayList<>();
        for (Stock stock : stocks) {
            String stockCode = stock.getStockCode();
            try {
                boolean calledApi = priceGapFillService.fillDomesticGap(stockCode);
                if (calledApi && !sleepBetweenStocks()) {
                    break;
                }
            } catch (Exception e) {
                handleDomesticFailure(stockCode, e, failures);
            }
            if (needsScoreRefresh(stockCode, latestPriceDate(stockCode))) {
                needsScoreRefresh.add(stockCode);
            }
        }
        scoreService.recalculateScores(needsScoreRefresh);
    }

    private void refreshOverseas(List<Stock> stocks, AtomicInteger failures) {
        List<String> needsScoreRefresh = new ArrayList<>();
        for (Stock stock : stocks) {
            String stockCode = stock.getStockCode();
            try {
                boolean calledApi = priceGapFillService.fillOverseasGap(stockCode);
                if (calledApi && !sleepBetweenStocks()) {
                    break;
                }
            } catch (Exception e) {
                handleOverseasFailure(stockCode, e, failures);
            }
            if (needsScoreRefresh(stockCode, latestOverseasPriceDate(stockCode))) {
                needsScoreRefresh.add(stockCode);
            }
        }
        scoreService.recalculateOverseasScores(needsScoreRefresh);
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
        // 레이트리밋/일시 장애 등으로 다수 종목이 한꺼번에 실패하면(예: Toss
        // 장애) 종목마다 풀 스택트레이스를 찍는 게 콘솔을 뒤덮어 정작 원인
        // 파악을 방해한다 - 메시지만 남기고, 배치 종료 시 refreshAll()의
        // 실패 건수 요약으로 규모를 파악한다. 원인 자체(스택트레이스)가
        // 필요하면 재현 후 debug 레벨로 임시 확인할 것.
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

    private boolean sleepBetweenStocks() {
        try {
            Thread.sleep(INTER_STOCK_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("가격 갱신 중단: 인터럽트 발생");
            return false;
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
     */
    private boolean needsScoreRefresh(String stockCode, Optional<LocalDate> latestPriceDate) {
        if (latestPriceDate.isEmpty()) {
            return false;
        }
        return scoreRepository.findTopByStockCodeOrderByScoreDateDesc(stockCode)
            .map(score -> score.getScoreDate().isBefore(latestPriceDate.get()))
            .orElse(true);
    }
}
