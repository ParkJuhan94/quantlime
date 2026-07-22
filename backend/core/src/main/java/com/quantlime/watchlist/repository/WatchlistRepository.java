package com.quantlime.watchlist.repository;

import com.quantlime.watchlist.domain.Watchlist;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    boolean existsByUser_IdAndStock_StockCode(Long userId, String stockCode);

    Optional<Watchlist> findByUser_IdAndStock_StockCode(Long userId, String stockCode);

    long countByUser_Id(Long userId);

    List<Watchlist> findAllByUser_IdAndGroup_Id(Long userId, Long groupId);

    // 레거시 미분류 행(그룹 도입 이전 등록분)을 찾아 기본 그룹으로 재배정하는
    // 자동 정리(WatchlistService.reconcileUngroupedWatchlist)에 쓰인다.
    List<Watchlist> findAllByUser_IdAndGroupIsNull(Long userId);

    List<Watchlist> findAllByUser_IdAndIdIn(Long userId, List<Long> ids);

    // 그룹(watchlist_group) 섹션 순서는 프론트에서 그룹 목록과 조합해 다시
    // 묶으므로, 여기선 그룹 유무와 무관하게 sortOrder 하나로만 정렬한다.
    @Query("select w from Watchlist w join fetch w.stock left join fetch w.group "
        + "where w.user.id = :userId order by w.sortOrder asc, w.id asc")
    List<Watchlist> findAllWithStockByUserId(@Param("userId") Long userId);

    @Query("select distinct w.stock.stockCode from Watchlist w")
    List<String> findDistinctStockCodes();

    // 검색모달 "인기 종목" - 관심종목으로 등록한 사용자 수(중복 등록 방지
    // 유니크 제약 덕분에 count(distinct user)=행 개수와 동일하지만 의미를
    // 명확히 하려 distinct를 명시)가 많은 순으로 상위 N개 종목 코드만
    // 뽑는다. limit는 Pageable로 전달(JPQL은 LIMIT 절을 직접 못 씀).
    @Query("select w.stock.stockCode from Watchlist w "
        + "group by w.stock.stockCode order by count(distinct w.user.id) desc")
    List<String> findStockCodesOrderByWatcherCountDesc(Pageable pageable);

    // 실시간 랭킹의 "관심종목만 보기" 토글용 - 한 사용자의 관심종목
    // 코드만 뽑아 MarketRankingCache 필터링에 쓴다.
    @Query("select w.stock.stockCode from Watchlist w where w.user.id = :userId")
    List<String> findStockCodesByUserId(@Param("userId") Long userId);
}
