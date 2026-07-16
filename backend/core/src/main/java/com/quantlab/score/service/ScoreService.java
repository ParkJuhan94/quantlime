package com.quantlab.score.service;

import com.quantlab.common.exception.NotFoundException;
import com.quantlab.infra.python.PythonEngineClient;
import com.quantlab.infra.python.dto.ScoreBatchApiRequest;
import com.quantlab.infra.python.dto.ScoreBatchApiResponse;
import com.quantlab.infra.python.dto.ScoreBatchApiResponse.StockScoreApiResponse;
import com.quantlab.price.domain.DailyPrice;
import com.quantlab.price.service.DailyPriceService;
import com.quantlab.score.domain.Score;
import com.quantlab.score.dto.mapper.ScoreMapper;
import com.quantlab.score.dto.mapper.ScoreRequestMapper;
import com.quantlab.score.dto.response.ScoreRankingResponse;
import com.quantlab.score.dto.response.ScoreResponse;
import com.quantlab.score.exception.ScoreErrorCode;
import com.quantlab.score.repository.ScoreRepository;
import com.quantlab.watchlist.domain.Watchlist;
import com.quantlab.watchlist.repository.WatchlistRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
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

    private final DailyPriceService dailyPriceService;
    private final PythonEngineClient pythonEngineClient;
    private final ScorePersistenceService scorePersistenceService;
    private final ScoreRepository scoreRepository;
    private final WatchlistRepository watchlistRepository;
    private final MeterRegistry meterRegistry;

    public void recalculateScore(String stockCode) {
        recalculateScores(List.of(stockCode));
    }

    public void recalculateWatchlistedScores() {
        List<String> stockCodes = watchlistRepository.findDistinctStockCodes();
        if (stockCodes.isEmpty()) {
            log.debug("스코어 일괄 재계산 스킵: 관심 종목 없음");
            return;
        }
        recalculateScores(stockCodes);
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
        Map<String, String> stockNameByCode = watchlist.stream()
            .collect(Collectors.toMap(
                w -> w.getStock().getStockCode(), w -> w.getStock().getStockName()));

        List<Score> latestScores = scoreRepository
            .findLatestScoresByStockCodesOrderByCompositeScoreDesc(
                stockNameByCode.keySet().stream().toList());

        return latestScores.stream()
            .map(score -> ScoreMapper.toScoreRankingResponse(
                score, stockNameByCode.get(score.getStockCode())))
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
