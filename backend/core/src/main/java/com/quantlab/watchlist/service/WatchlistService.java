package com.quantlab.watchlist.service;

import com.quantlab.common.exception.NotFoundException;
import com.quantlab.common.exception.ValidationException;
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

    // 이 메서드는 의도적으로 @Transactional을 붙이지 않는다. 등록 자체의 동시성
    // 안전장치는 트랜잭션 격리가 아니라 DB 유니크 제약 + DataIntegrityViolationException
    // 캐치이므로(TOCTOU 방어), 트랜잭션으로 감쌀 실익이 없다. 반면 아래에서 호출하는
    // 이력 백필은 외부 API 호출/재시도 대기가 있어, 여기에 @Transactional을 걸면
    // 그 시간만큼 DB 커넥션을 불필요하게 붙잡게 된다.
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

        backfillHistorySafely(stockCode);
        recalculateScoreSafely(stockCode);
        return watchlist;
    }

    private void backfillHistorySafely(String stockCode) {
        try {
            dailyPriceService.backfillHistoryIfNeeded(stockCode);
        } catch (Exception e) {
            // 백필 실패가 관심종목 등록 자체를 막지 않도록 로그만 남기고 넘어간다.
            // (스케줄러나 /dev/ohlcv/backfill로 나중에 재시도 가능)
            log.error("관심종목 등록 시 이력 백필 실패: stockCode={}, error={}",
                stockCode, e.getMessage(), e);
        }
    }

    private void recalculateScoreSafely(String stockCode) {
        try {
            scoreService.recalculateScore(stockCode);
        } catch (Exception e) {
            // 스코어 계산 실패(퀀트 엔진 장애 등)가 관심종목 등록 자체를 막지
            // 않도록 로그만 남기고 넘어간다. 저장된 이전 스코어가 없다면 조회
            // API가 NOT_FOUND_SCORE를 반환할 뿐, 등록 자체는 그대로 성공이다.
            log.error("관심종목 등록 시 스코어 계산 실패: stockCode={}, error={}",
                stockCode, e.getMessage(), e);
        }
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
