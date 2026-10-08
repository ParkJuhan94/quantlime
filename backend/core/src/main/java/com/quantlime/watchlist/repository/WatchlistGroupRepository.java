package com.quantlime.watchlist.repository;

import com.quantlime.watchlist.domain.WatchlistGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WatchlistGroupRepository extends JpaRepository<WatchlistGroup, Long> {

    List<WatchlistGroup> findAllByUser_IdOrderBySortOrderAsc(Long userId);

    Optional<WatchlistGroup> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_Id(Long userId);

    Optional<WatchlistGroup> findByUser_IdAndName(Long userId, String name);

    // 사분면 변화 알림을 켠 그룹을 하나라도 가진 사용자(중복 제거).
    @Query("select distinct g.user.id from WatchlistGroup g where g.quadrantAlertEnabled = true")
    List<Long> findUserIdsWithQuadrantAlertEnabled();
}
