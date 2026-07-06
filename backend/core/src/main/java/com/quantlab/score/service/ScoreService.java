package com.quantlab.score.service;

import com.quantlab.common.exception.NotFoundException;
import com.quantlab.infra.python.PythonEngineClient;
import com.quantlab.infra.python.dto.ScoreBatchApiRequest;
import com.quantlab.infra.python.dto.ScoreBatchApiRequest.StockScoreApiRequest;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreService {

    private static final int OHLCV_LOOKBACK_DAYS = 730;

    private final DailyPriceService dailyPriceService;
    private final PythonEngineClient pythonEngineClient;
    private final ScoreRepository scoreRepository;
    private final WatchlistRepository watchlistRepository;

    @Transactional
    public void recalculateScore(String stockCode) {
        List<DailyPrice> dailyPrices = fetchOhlcvHistory(stockCode);
        if (dailyPrices.isEmpty()) {
            log.warn("스코어 재계산 스킵: OHLCV 이력 없음, stockCode={}", stockCode);
            return;
        }

        StockScoreApiRequest request =
            ScoreRequestMapper.toStockScoreApiRequest(stockCode, dailyPrices);
        ScoreBatchApiResponse response = pythonEngineClient.calculateScoreBatch(
            new ScoreBatchApiRequest(List.of(request)));

        response.scores().stream()
            .filter(result -> result.stockCode().equals(stockCode))
            .findFirst()
            .ifPresent(this::saveScore);

        log.info("스코어 재계산 완료: stockCode={}", stockCode);
    }

    @Transactional
    public void recalculateWatchlistedScores() {
        List<String> stockCodes = watchlistRepository.findDistinctStockCodes();
        if (stockCodes.isEmpty()) {
            log.debug("스코어 일괄 재계산 스킵: 관심 종목 없음");
            return;
        }

        Map<String, List<DailyPrice>> dailyPricesByStockCode = stockCodes.stream()
            .collect(Collectors.toMap(Function.identity(), this::fetchOhlcvHistory));

        ScoreBatchApiRequest request =
            ScoreRequestMapper.toScoreBatchApiRequest(dailyPricesByStockCode);
        ScoreBatchApiResponse response = pythonEngineClient.calculateScoreBatch(request);

        response.scores().forEach(this::saveScore);
        log.info("스코어 일괄 재계산 완료: 대상종목수={}", stockCodes.size());
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

    private void saveScore(StockScoreApiResponse apiResponse) {
        LocalDate today = LocalDate.now();
        scoreRepository.findByStockCodeAndScoreDate(apiResponse.stockCode(), today)
            .ifPresentOrElse(
                existing -> ScoreMapper.updateScoreFrom(existing, apiResponse),
                () -> scoreRepository.save(
                    ScoreMapper.toScore(apiResponse.stockCode(), today, apiResponse)));
    }

    private List<DailyPrice> fetchOhlcvHistory(String stockCode) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(OHLCV_LOOKBACK_DAYS);
        return dailyPriceService.getDailyPrices(stockCode, start, end);
    }
}
