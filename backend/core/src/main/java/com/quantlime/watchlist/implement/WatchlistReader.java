package com.quantlime.watchlist.implement;

import com.quantlime.stock.domain.MarketType;
import com.quantlime.watchlist.domain.Watchlist;
import com.quantlime.watchlist.repository.WatchlistRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * 관심 종목 조회를 감싸는 구현 레이어(Implementation). 메서드 이름은 Repository와 같게 두어
 * 호출부 변환이 위임 한 겹으로 끝나게 했다.
 */
@Component
@RequiredArgsConstructor
public class WatchlistReader {

    private final WatchlistRepository watchlistRepository;

    /** 시장 구분별로 누군가 관심 등록한 종목코드(중복 제거). */
    public List<String> findDistinctStockCodesByMarketTypeIn(List<MarketType> marketTypes) {
        return watchlistRepository.findDistinctStockCodesByMarketTypeIn(marketTypes);
    }

    public boolean existsByUser_IdAndStock_StockCode(Long userId, String stockCode) {
        return watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode);
    }

    public Optional<Watchlist> findByUser_IdAndStock_StockCode(Long userId, String stockCode) {
        return watchlistRepository.findByUser_IdAndStock_StockCode(userId, stockCode);
    }

    public long countByUser_Id(Long userId) {
        return watchlistRepository.countByUser_Id(userId);
    }

    public List<Watchlist> findAllByUser_IdAndGroup_Id(Long userId, Long groupId) {
        return watchlistRepository.findAllByUser_IdAndGroup_Id(userId, groupId);
    }

    public List<Watchlist> findAllByUser_IdAndGroupIsNull(Long userId) {
        return watchlistRepository.findAllByUser_IdAndGroupIsNull(userId);
    }

    public List<Watchlist> findAllByUser_IdAndIdIn(Long userId, List<Long> ids) {
        return watchlistRepository.findAllByUser_IdAndIdIn(userId, ids);
    }

    public List<Watchlist> findAllWithStockByUserId(Long userId) {
        return watchlistRepository.findAllWithStockByUserId(userId);
    }

    public List<String> findStockCodesInQuadrantAlertGroups(Long userId) {
        return watchlistRepository.findStockCodesInQuadrantAlertGroups(userId);
    }

    public List<String> findDistinctStockCodes() {
        return watchlistRepository.findDistinctStockCodes();
    }

    public List<String> findStockCodesOrderByWatcherCountDesc(Pageable pageable) {
        return watchlistRepository.findStockCodesOrderByWatcherCountDesc(pageable);
    }

    public List<String> findStockCodesByUserId(Long userId) {
        return watchlistRepository.findStockCodesByUserId(userId);
    }
}
