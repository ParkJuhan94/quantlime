package com.quantlime.score.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.ScoreBatchApiRequest;
import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse;
import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse.StockScoreSeriesApiResponse;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.price.service.DomesticDailyPriceService;
import com.quantlime.score.domain.Score;
import com.quantlime.score.dto.mapper.ScoreMapper;
import com.quantlime.score.dto.mapper.ScoreRequestMapper;
import com.quantlime.score.dto.response.ScoreRankingResponse;
import com.quantlime.score.dto.response.ScoreResponse;
import com.quantlime.score.exception.ScoreErrorCode;
import com.quantlime.score.repository.ScoreRepository;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.dto.mapper.StockMapper;
import com.quantlime.stock.service.StockMasterService;
import com.quantlime.watchlist.domain.Watchlist;
import com.quantlime.watchlist.repository.WatchlistRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관심 종목의 OHLCV 이력을 퀀트 엔진(Python)에 넘겨 스코어를 계산·영속화한다.
 *
 * <p>Python 엔진 호출이 실패하면 이번 재계산만 건너뛰고 기존에 저장된 최신
 * 스코어 행은 그대로 남는다 - 별도의 캐시 계층 없이 "직전 이력이 곧 fallback"이
 * 되는 구조(CLAUDE.md §10). 호출 실패 시의 격리(로그만 남기고 흐름은 계속)는
 * 이 서비스를 부르는 쪽(WatchlistService, 스케줄러)의 책임이다.
 *
 * <p>OHLCV 조회 + 퀀트 엔진 HTTP 호출은 트랜잭션 밖에서 수행한다. 저장만
 * {@link ScorePersistenceService}의 별도 트랜잭션으로 처리해, 외부 호출 왕복
 * 시간 동안 DB 커넥션을 붙잡지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreService {

    private static final int OHLCV_LOOKBACK_DAYS = 730;
    private static final String METRIC_MISSING_FROM_RESPONSE = "score.batch.missing-from-response";
    // 전종목(약 2,700개)을 한 요청에 다 넣으면 퀀트 엔진에 보내는 JSON
    // 페이로드가 지나치게 커진다(종목당 최대 730일 OHLCV) - 청크로 나눠
    // 순차 호출하고, 한 청크가 실패해도 나머지 청크는 계속 진행한다
    // (OhlcvCollectorScheduler의 종목별 try-catch와 동일한 격리 원칙).
    private static final int SCORE_BATCH_CHUNK_SIZE = 100;

    private final DomesticDailyPriceService domesticDailyPriceService;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;
    private final PythonEngineClient pythonEngineClient;
    private final ScorePersistenceService scorePersistenceService;
    private final ScoreRepository scoreRepository;
    private final WatchlistRepository watchlistRepository;
    private final StockMasterService stockMasterService;
    private final MeterRegistry meterRegistry;

    public void recalculateDomesticScore(String stockCode) {
        recalculateDomesticScoresChunk(List.of(stockCode));
    }

    // 관심종목만이 아니라 전 상장종목을 대상으로 계산한다(2026-07-16 -
    // 이전엔 관심종목만 계산해 등록 안 한 종목은 스코어 자체가 없었음).
    // 일봉 마감 기준 지표라 매일 배치 한 번이면 충분하다(OhlcvCollectorScheduler
    // 참고, 하루 한 번 16:00 실행).
    public void recalculateAllListedScores() {
        List<String> stockCodes = stockMasterService.getAllListedStocks().stream()
            .map(Stock::getStockCode)
            .toList();
        recalculateDomesticScores(stockCodes);
    }

    /**
     * 전달된 종목들만 청크 단위로 재계산한다 - {@code recalculateAllListedScores}처럼
     * 전종목을 매번 무조건 다시 계산하는 대신, 호출측(MarketDataRefreshService 등)이
     * 실제로 갱신이 필요한 종목만 걸러서 넘기면 같은 배치/격리 로직을 그대로
     * 재사용할 수 있다.
     */
    public void recalculateDomesticScores(List<String> stockCodes) {
        if (stockCodes.isEmpty()) {
            log.debug("스코어 재계산 스킵: 대상 종목 없음");
            return;
        }

        List<List<String>> chunks = partition(stockCodes, SCORE_BATCH_CHUNK_SIZE);
        log.info("스코어 재계산 시작: 대상종목수={}, 청크수={}", stockCodes.size(), chunks.size());
        int chunkIndex = 0;
        for (List<String> chunk : chunks) {
            chunkIndex++;
            try {
                recalculateDomesticScoresChunk(chunk);
            } catch (Exception e) {
                log.error("스코어 재계산 청크 실패(다음 청크는 계속 진행): chunkIndex={}/{}, error={}",
                    chunkIndex, chunks.size(), e.getMessage(), e);
            }
        }
        log.info("스코어 재계산 완료: 총 {}개 청크", chunks.size());
    }

    /**
     * 해외종목 버전 - OHLCV를 {@code overseas_daily_price}에서 조회하는 것만
     * 다르고, 청크/격리/영속화 로직은 {@link #recalculateDomesticScores}와 동일하다
     * (PriceGapFillService의 국내/해외 분리 패턴과 동일한 이유).
     */
    public void recalculateOverseasScores(List<String> stockCodes) {
        if (stockCodes.isEmpty()) {
            log.debug("해외 스코어 재계산 스킵: 대상 종목 없음");
            return;
        }

        List<List<String>> chunks = partition(stockCodes, SCORE_BATCH_CHUNK_SIZE);
        log.info("해외 스코어 재계산 시작: 대상종목수={}, 청크수={}", stockCodes.size(), chunks.size());
        int chunkIndex = 0;
        for (List<String> chunk : chunks) {
            chunkIndex++;
            try {
                recalculateOverseasScoresChunk(chunk);
            } catch (Exception e) {
                log.error("해외 스코어 재계산 청크 실패(다음 청크는 계속 진행): chunkIndex={}/{}, error={}",
                    chunkIndex, chunks.size(), e.getMessage(), e);
            }
        }
        log.info("해외 스코어 재계산 완료: 총 {}개 청크", chunks.size());
    }

    private static List<List<String>> partition(List<String> list, int size) {
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
    }

    @Transactional(readOnly = true)
    public ScoreResponse getScore(String stockCode) {
        Score score = scoreRepository.findTopByStockCodeOrderByScoreDateDesc(stockCode)
            .orElseThrow(() -> new NotFoundException(ScoreErrorCode.NOT_FOUND_SCORE));
        return ScoreMapper.toScoreResponse(score);
    }

    @Transactional(readOnly = true)
    public List<ScoreRankingResponse> getDashboardScores(Long userId, String scope) {
        List<Watchlist> watchlist = watchlistRepository.findAllWithStockByUserId(userId);
        Map<String, Stock> stockByCode = watchlist.stream()
            .map(Watchlist::getStock)
            .filter(stock -> matchesScope(stock.getMarketType(), scope))
            .collect(Collectors.toMap(Stock::getStockCode, stock -> stock));

        List<Score> latestScores = scoreRepository
            .findLatestScoresByStockCodesOrderByCompositeScoreDesc(
                stockByCode.keySet().stream().toList());

        return latestScores.stream()
            .map(score -> {
                Stock stock = stockByCode.get(score.getStockCode());
                return ScoreMapper.toScoreRankingResponse(
                    score, stock.getDisplayName(), stock.getSector(), StockMapper.toLogoUrl(stock),
                    !stock.getMarketType().isDomestic());
            })
            .toList();
    }

    // "실시간 랭킹" 스코어 탭의 "전체" 토글 - 관심종목 여부와 무관하게 전
    // 상장종목 중 상위 N개(2026-07-18, 관심종목만/전체 토글로 /dashboard
    // 별도 페이지를 대체).
    @Transactional(readOnly = true)
    public List<ScoreRankingResponse> getAllStocksScoreRanking(int limit, String scope) {
        List<Score> latestScores = scoreRepository
            .findTopScoresOrderByCompositeScoreDesc(limit, scopeToMarketTypes(scope));
        List<String> stockCodes = latestScores.stream().map(Score::getStockCode).toList();
        Map<String, Stock> stockByCode = stockMasterService.getStocksByCodesInOrder(stockCodes).stream()
            .collect(Collectors.toMap(Stock::getStockCode, stock -> stock));

        return latestScores.stream()
            .map(score -> {
                Stock stock = stockByCode.get(score.getStockCode());
                return ScoreMapper.toScoreRankingResponse(
                    score, stock.getDisplayName(), stock.getSector(), StockMapper.toLogoUrl(stock),
                    !stock.getMarketType().isDomestic());
            })
            .toList();
    }

    /**
     * "all"(또는 null)이면 시장 구분 없이 전부, "domestic"/"overseas"면 그
     * 시장만 - {@code MarketController.getRanking}의 scope 파라미터와
     * 동일한 값 집합을 쓴다(2026-07-30 추가 - 이 필터가 없으면 국내/해외
     * 스코어 분포 차이 때문에 상위 N개가 한쪽 시장으로만 쏠렸음).
     */
    private List<MarketType> scopeToMarketTypes(String scope) {
        if ("domestic".equals(scope)) {
            return MarketType.domesticValues();
        }
        if ("overseas".equals(scope)) {
            return MarketType.overseasValues();
        }
        return null;
    }

    private boolean matchesScope(MarketType marketType, String scope) {
        List<MarketType> allowed = scopeToMarketTypes(scope);
        return allowed == null || allowed.contains(marketType);
    }

    private void recalculateDomesticScoresChunk(List<String> stockCodes) {
        Map<String, List<DomesticDailyPrice>> domesticDailyPricesByStockCode = fetchDomesticOhlcvHistories(stockCodes);
        if (domesticDailyPricesByStockCode.isEmpty()) {
            log.info("스코어 재계산 스킵: OHLCV 이력이 있는 종목 없음, stockCodes={}", stockCodes);
            return;
        }
        warnIfMissingHistory(stockCodes, domesticDailyPricesByStockCode.keySet());

        ScoreBatchApiRequest request =
            ScoreRequestMapper.toScoreBatchApiRequest(domesticDailyPricesByStockCode);
        ScoreSeriesBatchApiResponse response = pythonEngineClient.calculateScoreSeries(request);

        warnIfMissingFromResponse(domesticDailyPricesByStockCode.keySet(), response.scores());
        scorePersistenceService.saveAll(response.scores());

        log.info("스코어 재계산 완료: 대상종목수={}", domesticDailyPricesByStockCode.size());
    }

    /**
     * OHLCV 이력이 아예 없는 종목(막 등록되어 백필이 안 끝난 경우 등)은
     * 배치 요청에서 제외한다 - 포함하면 퀀트 엔진이 빈 OHLCV로 계산을 시도하다
     * 실패해 같은 배치에 포함된 다른 모든 종목의 스코어까지 갱신되지 못한다.
     */
    private Map<String, List<DomesticDailyPrice>> fetchDomesticOhlcvHistories(List<String> stockCodes) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(OHLCV_LOOKBACK_DAYS);
        return domesticDailyPriceService.getDailyPrices(stockCodes, start, end).stream()
            .collect(Collectors.groupingBy(DomesticDailyPrice::getStockCode));
    }

    private void recalculateOverseasScoresChunk(List<String> stockCodes) {
        Map<String, List<OverseasDailyPrice>> pricesByStockCode = fetchOverseasOhlcvHistories(stockCodes);
        if (pricesByStockCode.isEmpty()) {
            log.info("해외 스코어 재계산 스킵: OHLCV 이력이 있는 종목 없음, stockCodes={}", stockCodes);
            return;
        }
        warnIfMissingHistory(stockCodes, pricesByStockCode.keySet());

        ScoreBatchApiRequest request = ScoreRequestMapper.toOverseasScoreBatchApiRequest(pricesByStockCode);
        ScoreSeriesBatchApiResponse response = pythonEngineClient.calculateScoreSeries(request);

        warnIfMissingFromResponse(pricesByStockCode.keySet(), response.scores());
        scorePersistenceService.saveAll(response.scores());

        log.info("해외 스코어 재계산 완료: 대상종목수={}", pricesByStockCode.size());
    }

    private Map<String, List<OverseasDailyPrice>> fetchOverseasOhlcvHistories(List<String> stockCodes) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(OHLCV_LOOKBACK_DAYS);
        return overseasDailyPriceRepository
            .findByStockCodeInAndTradeDateBetweenOrderByTradeDateDesc(stockCodes, start, end)
            .stream()
            .collect(Collectors.groupingBy(OverseasDailyPrice::getStockCode));
    }

    private void warnIfMissingHistory(List<String> requested, Set<String> withHistory) {
        List<String> skipped = requested.stream()
            .filter(code -> !withHistory.contains(code))
            .toList();
        if (!skipped.isEmpty()) {
            log.warn("스코어 재계산 제외(OHLCV 이력 없음): stockCodes={}", skipped);
        }
    }

    private void warnIfMissingFromResponse(Set<String> requested, List<StockScoreSeriesApiResponse> results) {
        Set<String> responded = results.stream()
            .map(StockScoreSeriesApiResponse::stockCode)
            .collect(Collectors.toSet());
        List<String> missing = requested.stream()
            .filter(code -> !responded.contains(code))
            .toList();
        if (!missing.isEmpty()) {
            log.warn("퀀트 엔진 응답에 누락된 종목 존재(저장 스킵): stockCodes={}", missing);
            meterRegistry.counter(METRIC_MISSING_FROM_RESPONSE).increment(missing.size());
        }
    }
}
