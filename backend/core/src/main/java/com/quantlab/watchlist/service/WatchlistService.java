package com.quantlab.watchlist.service;

import com.quantlab.common.exception.NotFoundException;
import com.quantlab.common.exception.ValidationException;
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

    @Transactional
    public Watchlist addWatchlist(Long userId, String stockCode) {
        User user = userService.getById(userId);
        Stock stock = stockMasterService.getStockByCode(stockCode);

        if (watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode)) {
            throw new ValidationException(WatchlistErrorCode.ALREADY_EXISTS_WATCHLIST);
        }

        try {
            Watchlist watchlist = watchlistRepository.save(Watchlist.of(user, stock));
            log.info("관심종목 등록 완료: userId={}, stockCode={}", userId, stockCode);
            return watchlist;
        } catch (DataIntegrityViolationException e) {
            throw new ValidationException(WatchlistErrorCode.ALREADY_EXISTS_WATCHLIST);
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
