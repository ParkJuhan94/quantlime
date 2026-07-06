package com.quantlab.watchlist.repository;

import com.quantlab.watchlist.domain.Watchlist;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    boolean existsByUser_IdAndStock_StockCode(Long userId, String stockCode);

    Optional<Watchlist> findByUser_IdAndStock_StockCode(Long userId, String stockCode);

    @Query("select w from Watchlist w join fetch w.stock "
        + "where w.user.id = :userId order by w.createdAt desc, w.id desc")
    List<Watchlist> findAllWithStockByUserId(@Param("userId") Long userId);

    @Query("select distinct w.stock.stockCode from Watchlist w")
    List<String> findDistinctStockCodes();
}
