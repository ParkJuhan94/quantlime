package com.quantlime.watchlist.implement;

import com.quantlime.watchlist.domain.WatchlistGroup;
import com.quantlime.watchlist.repository.WatchlistGroupRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 관심 종목 그룹 조회를 감싸는 구현 레이어. 메서드 이름은 Repository와 같다. */
@Component
@RequiredArgsConstructor
public class WatchlistGroupReader {

    private final WatchlistGroupRepository watchlistGroupRepository;

    public List<WatchlistGroup> findAllByUser_IdOrderBySortOrderAsc(Long userId) {
        return watchlistGroupRepository.findAllByUser_IdOrderBySortOrderAsc(userId);
    }

    public Optional<WatchlistGroup> findByIdAndUser_Id(Long id, Long userId) {
        return watchlistGroupRepository.findByIdAndUser_Id(id, userId);
    }

    public long countByUser_Id(Long userId) {
        return watchlistGroupRepository.countByUser_Id(userId);
    }

    public List<Long> findUserIdsWithQuadrantAlertEnabled() {
        return watchlistGroupRepository.findUserIdsWithQuadrantAlertEnabled();
    }

    public Optional<WatchlistGroup> findByUser_IdAndName(Long userId, String name) {
        return watchlistGroupRepository.findByUser_IdAndName(userId, name);
    }
}
