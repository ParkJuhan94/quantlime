package com.quantlime.watchlist.repository;

import com.quantlime.watchlist.domain.WatchlistGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistGroupRepository extends JpaRepository<WatchlistGroup, Long> {

    List<WatchlistGroup> findAllByUser_IdOrderBySortOrderAsc(Long userId);

    Optional<WatchlistGroup> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_Id(Long userId);

    Optional<WatchlistGroup> findByUser_IdAndName(Long userId, String name);
}
