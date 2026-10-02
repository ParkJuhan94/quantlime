package com.quantlime.feed.repository;

import com.quantlime.feed.domain.FeedCategory;
import com.quantlime.feed.domain.FeedPost;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedPostRepository extends JpaRepository<FeedPost, Long> {

    @Query("select p from FeedPost p join fetch p.user where p.category = :category and p.hidden = false order by p.id desc")
    Slice<FeedPost> findByCategoryOrderByIdDesc(@Param("category") FeedCategory category, Pageable pageable);

    @Query("select p from FeedPost p join fetch p.user where p.hidden = false order by p.id desc")
    Slice<FeedPost> findAllOrderByIdDesc(Pageable pageable);

    // 수정/삭제 소유권 검증용 - 다른 사용자의 글이면 조회 자체가 안 돼
    // WatchlistGroupService.getOwnedGroup과 동일하게 403이 아니라 404로
    // 응답한다(다른 사용자 글의 존재 여부를 노출하지 않기 위함).
    Optional<FeedPost> findByIdAndUser_Id(Long id, Long userId);

    // 숨김 처리된 글은 신고/좋아요 같은 일반 사용자 동작의 대상에서 제외한다.
    Optional<FeedPost> findByIdAndHiddenFalse(Long id);

    // 관리자 검토 목록 - 신고 누적 등으로 숨겨진 글.
    @Query("select p from FeedPost p join fetch p.user where p.hidden = true order by p.id desc")
    Slice<FeedPost> findHiddenOrderByIdDesc(Pageable pageable);
}
