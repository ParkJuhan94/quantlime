package com.quantlab.watchlist.service;

import com.quantlab.common.exception.NotFoundException;
import com.quantlab.common.exception.ValidationException;
import com.quantlab.common.util.SafeExecutor;
import com.quantlab.price.service.DailyPriceService;
import com.quantlab.score.service.ScoreService;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.service.StockMasterService;
import com.quantlab.user.domain.User;
import com.quantlab.user.service.UserService;
import com.quantlab.watchlist.domain.Watchlist;
import com.quantlab.watchlist.exception.WatchlistErrorCode;
import com.quantlab.watchlist.repository.WatchlistRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final UserService userService;
    private final StockMasterService stockMasterService;
    private final WatchlistRepository watchlistRepository;
    private final DailyPriceService dailyPriceService;
    private final ScoreService scoreService;
    private final TaskExecutor watchlistTaskExecutor;

    // 이 메서드는 의도적으로 @Transactional을 붙이지 않는다. 등록 자체의 동시성
    // 안전장치는 트랜잭션 격리가 아니라 DB 유니크 제약 + DataIntegrityViolationException
    // 캐치이므로(TOCTOU 방어), 트랜잭션으로 감쌀 실익이 없다.
    public Watchlist addWatchlist(Long userId, String stockCode) {
        User user = userService.getById(userId);
        Stock stock = stockMasterService.getStockByCode(stockCode);

        if (watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode)) {
            throw new ValidationException(WatchlistErrorCode.ALREADY_EXISTS_WATCHLIST);
        }

        Watchlist watchlist;
        try {
            watchlist = watchlistRepository.save(Watchlist.of(user, stock));
            log.info("관심종목 등록 완료: userId={}, stockCode={}", userId, stockCode);
        } catch (DataIntegrityViolationException e) {
            throw new ValidationException(WatchlistErrorCode.ALREADY_EXISTS_WATCHLIST);
        }

        // 이력 백필(Toss 다중 페이지 호출)과 스코어 계산(퀀트 엔진 호출)은 둘 다
        // 외부 API 왕복을 포함해 수 초가 걸릴 수 있다. 등록 응답을 그 시간만큼
        // 붙잡지 않도록 별도 스레드에서 순차 실행한다(백필이 끝난 뒤 스코어를
        // 계산해야 최신 OHLCV로 계산되므로 순서는 유지).
        watchlistTaskExecutor.execute(() -> runPostRegistrationTasksSafely(stockCode));
        return watchlist;
    }

    private void runPostRegistrationTasksSafely(String stockCode) {
        SafeExecutor.runSafely(
            "관심종목 등록 시 이력 백필(stockCode=" + stockCode + ")",
            () -> dailyPriceService.backfillHistoryIfNeeded(stockCode));
        SafeExecutor.runSafely(
            "관심종목 등록 시 스코어 계산(stockCode=" + stockCode + ")",
            () -> scoreService.recalculateScore(stockCode));
    }

    @Transactional
    public void removeWatchlist(Long userId, String stockCode) {
        Watchlist watchlist = watchlistRepository
            .findByUser_IdAndStock_StockCode(userId, stockCode)
            .orElseThrow(() -> new NotFoundException(WatchlistErrorCode.NOT_FOUND_WATCHLIST));
        watchlistRepository.delete(watchlist);
        log.info("관심종목 해제 완료: userId={}, stockCode={}", userId, stockCode);
    }

    @Transactional(readOnly = true)
    public List<Watchlist> getWatchlist(Long userId) {
        return watchlistRepository.findAllWithStockByUserId(userId);
    }
}
