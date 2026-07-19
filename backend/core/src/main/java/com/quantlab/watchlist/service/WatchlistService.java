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
import com.quantlab.watchlist.domain.WatchlistGroup;
import com.quantlab.watchlist.exception.WatchlistErrorCode;
import com.quantlab.watchlist.repository.WatchlistRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final UserService userService;
    private final StockMasterService stockMasterService;
    private final WatchlistRepository watchlistRepository;
    private final WatchlistGroupService watchlistGroupService;
    private final DailyPriceService dailyPriceService;
    private final ScoreService scoreService;
    private final TaskExecutor watchlistTaskExecutor;

    // 이 메서드는 의도적으로 @Transactional을 붙이지 않는다. 등록 자체의 동시성
    // 안전장치는 트랜잭션 격리가 아니라 DB 유니크 제약 + DataIntegrityViolationException
    // 캐치이므로(TOCTOU 방어), 트랜잭션으로 감쌀 실익이 없다.
    //
    // groupId는 필수다("미분류" 폐지 - 2026-07-14) - 프론트는 등록 전에
    // 항상 그룹을 고르게(그룹이 하나도 없으면 먼저 만들게) 강제하고,
    // 여기서도 그 불변식을 서버측에서 한 번 더 검증한다.
    public Watchlist addWatchlist(Long userId, String stockCode, Long groupId) {
        User user = userService.getById(userId);
        Stock stock = stockMasterService.getStockByCode(stockCode);
        WatchlistGroup group = watchlistGroupService.getOwnedGroup(userId, groupId);

        if (watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode)) {
            throw new ValidationException(WatchlistErrorCode.ALREADY_EXISTS_WATCHLIST);
        }

        Watchlist watchlist;
        try {
            // 새 종목은 목록 맨 뒤에 추가되도록 현재 개수를 다음 sortOrder로 쓴다.
            int nextSortOrder = (int) watchlistRepository.countByUser_Id(userId);
            watchlist = watchlistRepository.save(Watchlist.of(user, stock, group, nextSortOrder));
            log.info("관심종목 등록 완료: userId={}, stockCode={}, groupId={}", userId, stockCode, groupId);
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

    /**
     * 그룹 도입 이전에 등록된 레거시 행은 group이 null로 남아있을 수 있다
     * ("미분류" 폐지 이후로는 새로 생기지 않지만, 과거 데이터는 자동으로
     * 정리되지 않으므로 조회 시점에 자가 치유한다). 평소(정리할 미분류가
     * 없는 상태)에는 추가 쿼리 없이 그대로 반환한다.
     */
    @Transactional
    public List<Watchlist> getWatchlist(Long userId) {
        reconcileUngroupedWatchlist(userId);
        return watchlistRepository.findAllWithStockByUserId(userId);
    }

    private void reconcileUngroupedWatchlist(Long userId) {
        List<Watchlist> ungrouped = watchlistRepository.findAllByUser_IdAndGroupIsNull(userId);
        if (ungrouped.isEmpty()) {
            return;
        }
        WatchlistGroup defaultGroup = watchlistGroupService.findOrCreateDefaultGroup(userId);
        ungrouped.forEach(item -> item.assignToGroup(defaultGroup));
        log.info("미분류 관심종목을 기본 그룹으로 자동 이동: userId={}, 이동건수={}", userId, ungrouped.size());
    }

    @Transactional
    public void moveToGroup(Long userId, String stockCode, Long groupId) {
        Watchlist watchlist = watchlistRepository
            .findByUser_IdAndStock_StockCode(userId, stockCode)
            .orElseThrow(() -> new NotFoundException(WatchlistErrorCode.NOT_FOUND_WATCHLIST));
        WatchlistGroup group = watchlistGroupService.getOwnedGroup(userId, groupId);
        watchlist.assignToGroup(group);
    }

    // 프론트는 드래그로 재배열한 한 그룹(또는 미분류) 안의 watchlistId
    // 목록을 새 순서 그대로 보낸다 - sortOrder는 그룹 경계를 넘나들며
    // 전역으로 유일할 필요가 없다(항상 같은 그룹 안에서만 비교되므로).
    @Transactional
    public void reorderWatchlist(Long userId, List<Long> watchlistIds) {
        List<Watchlist> items = watchlistRepository.findAllByUser_IdAndIdIn(userId, watchlistIds);
        Map<Long, Watchlist> itemById = new HashMap<>();
        items.forEach(item -> itemById.put(item.getId(), item));

        for (int i = 0; i < watchlistIds.size(); i++) {
            Watchlist item = itemById.get(watchlistIds.get(i));
            if (item != null) {
                item.updateSortOrder(i);
            }
        }
    }

    // 검색모달 "인기 종목" - 관심종목 등록자 수가 많은 순으로 상위 N개.
    // 정렬 기준(watcherCount) 자체는 응답에 노출하지 않고 순서로만 반영한다.
    @Transactional(readOnly = true)
    public List<Stock> getPopularStocks(int limit) {
        List<String> stockCodes = watchlistRepository.findStockCodesOrderByWatcherCountDesc(PageRequest.of(0, limit));
        return stockMasterService.getStocksByCodesInOrder(stockCodes);
    }

    // 실시간 랭킹의 "관심종목만 보기" 토글용 - MarketRankingService가 이
    // 코드 집합으로 전종목 랭킹을 필터링한다.
    @Transactional(readOnly = true)
    public Set<String> getWatchlistStockCodes(Long userId) {
        return Set.copyOf(watchlistRepository.findStockCodesByUserId(userId));
    }
}
