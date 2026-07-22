package com.quantlime.score.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.ScoreBatchApiRequest;
import com.quantlime.infra.python.dto.ScoreBatchApiResponse;
import com.quantlime.infra.python.dto.ScoreBatchApiResponse.StockScoreApiResponse;
import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.service.DailyPriceService;
import com.quantlime.score.domain.Score;
import com.quantlime.score.dto.mapper.ScoreMapper;
import com.quantlime.score.dto.mapper.ScoreRequestMapper;
import com.quantlime.score.dto.response.ScoreRankingResponse;
import com.quantlime.score.dto.response.ScoreResponse;
import com.quantlime.score.exception.ScoreErrorCode;
import com.quantlime.score.repository.ScoreRepository;
import com.quantlime.stock.domain.Stock;
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

    private final DailyPriceService dailyPriceService;
    private final PythonEngineClient pythonEngineClient;
    private final ScorePersistenceService scorePersistenceService;
    private final ScoreRepository scoreRepository;
    private final WatchlistRepository watchlistRepository;
    private final StockMasterService stockMasterService;
    private final MeterRegistry meterRegistry;

    public void recalculateScore(String stockCode) {
        recalculateScores(List.of(stockCode));
    }

    // 관심종목만이 아니라 전 상장종목을 대상으로 계산한다(2026-07-16 -
    // 이전엔 관심종목만 계산해 등록 안 한 종목은 스코어 자체가 없었음).
    // 일봉 마감 기준 지표라 매일 배치 한 번이면 충분하다(OhlcvCollectorScheduler
    // 참고, 하루 한 번 16:00 실행).
    public void recalculateAllListedScores() {
        List<String> stockCodes = stockMasterService.getAllListedStocks().stream()
            .map(Stock::getStockCode)
            .toList();
        if (stockCodes.isEmpty()) {
            log.debug("스코어 일괄 재계산 스킵: 상장 종목 없음");
            return;
        }

        List<List<String>> chunks = partition(stockCodes, SCORE_BATCH_CHUNK_SIZE);
        log.info("전종목 스코어 재계산 시작: 대상종목수={}, 청크수={}", stockCodes.size(), chunks.size());
        int chunkIndex = 0;
        for (List<String> chunk : chunks) {
            chunkIndex++;
            try {
                recalculateScores(chunk);
            } catch (Exception e) {
                log.error("스코어 재계산 청크 실패(다음 청크는 계속 진행): chunkIndex={}/{}, error={}",
                    chunkIndex, chunks.size(), e.getMessage(), e);
            }
        }
        log.info("전종목 스코어 재계산 완료: 총 {}개 청크", chunks.size());
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
    public List<ScoreRankingResponse> getDashboardScores(Long userId) {
        List<Watchlist> watchlist = watchlistRepository.findAllWithStockByUserId(userId);
        Map<String, Stock> stockByCode = watchlist.stream()
            .collect(Collectors.toMap(w -> w.getStock().getStockCode(), Watchlist::getStock));

        List<Score> latestScores = scoreRepository
            .findLatestScoresByStockCodesOrderByCompositeScoreDesc(
                stockByCode.keySet().stream().toList());

        return latestScores.stream()
            .map(score -> {
                Stock stock = stockByCode.get(score.getStockCode());
                return ScoreMapper.toScoreRankingResponse(score, stock.getStockName(), stock.getSector());
            })
            .toList();
    }

    // "실시간 랭킹" 스코어 탭의 "전체" 토글 - 관심종목 여부와 무관하게 전
    // 상장종목 중 상위 N개(2026-07-18, 관심종목만/전체 토글로 /dashboard
    // 별도 페이지를 대체).
    @Transactional(readOnly = true)
    public List<ScoreRankingResponse> getAllStocksScoreRanking(int limit) {
        List<Score> latestScores = scoreRepository.findTopScoresOrderByCompositeScoreDesc(limit);
        List<String> stockCodes = latestScores.stream().map(Score::getStockCode).toList();
        Map<String, Stock> stockByCode = stockMasterService.getStocksByCodesInOrder(stockCodes).stream()
            .collect(Collectors.toMap(Stock::getStockCode, stock -> stock));

        return latestScores.stream()
            .map(score -> {
                Stock stock = stockByCode.get(score.getStockCode());
                return ScoreMapper.toScoreRankingResponse(score, stock.getStockName(), stock.getSector());
            })
            .toList();
    }

    private void recalculateScores(List<String> stockCodes) {
        Map<String, List<DailyPrice>> dailyPricesByStockCode = fetchOhlcvHistories(stockCodes);
        if (dailyPricesByStockCode.isEmpty()) {
            log.info("스코어 재계산 스킵: OHLCV 이력이 있는 종목 없음, stockCodes={}", stockCodes);
            return;
        }
        warnIfMissingHistory(stockCodes, dailyPricesByStockCode.keySet());

        ScoreBatchApiRequest request =
            ScoreRequestMapper.toScoreBatchApiRequest(dailyPricesByStockCode);
        ScoreBatchApiResponse response = pythonEngineClient.calculateScoreBatch(request);

        warnIfMissingFromResponse(dailyPricesByStockCode.keySet(), response.scores());
        scorePersistenceService.saveAll(response.scores());

        log.info("스코어 재계산 완료: 대상종목수={}", dailyPricesByStockCode.size());
    }

    /**
     * OHLCV 이력이 아예 없는 종목(막 등록되어 백필이 안 끝난 경우 등)은
     * 배치 요청에서 제외한다 - 포함하면 퀀트 엔진이 빈 OHLCV로 계산을 시도하다
     * 실패해 같은 배치에 포함된 다른 모든 종목의 스코어까지 갱신되지 못한다.
     */
    private Map<String, List<DailyPrice>> fetchOhlcvHistories(List<String> stockCodes) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(OHLCV_LOOKBACK_DAYS);
        return dailyPriceService.getDailyPrices(stockCodes, start, end).stream()
            .collect(Collectors.groupingBy(DailyPrice::getStockCode));
    }

    private void warnIfMissingHistory(List<String> requested, Set<String> withHistory) {
        List<String> skipped = requested.stream()
            .filter(code -> !withHistory.contains(code))
            .toList();
        if (!skipped.isEmpty()) {
            log.warn("스코어 재계산 제외(OHLCV 이력 없음): stockCodes={}", skipped);
        }
    }

    private void warnIfMissingFromResponse(Set<String> requested, List<StockScoreApiResponse> results) {
        Set<String> responded = results.stream()
            .map(StockScoreApiResponse::stockCode)
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
