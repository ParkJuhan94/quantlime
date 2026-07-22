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

    @Query("select p from FeedPost p join fetch p.user where p.category = :category order by p.id desc")
    Slice<FeedPost> findByCategoryOrderByIdDesc(@Param("category") FeedCategory category, Pageable pageable);

    @Query("select p from FeedPost p join fetch p.user order by p.id desc")
    Slice<FeedPost> findAllOrderByIdDesc(Pageable pageable);

    // 수정/삭제 소유권 검증용 - 다른 사용자의 글이면 조회 자체가 안 돼
    // WatchlistGroupService.getOwnedGroup과 동일하게 403이 아니라 404로
    // 응답한다(다른 사용자 글의 존재 여부를 노출하지 않기 위함).
    Optional<FeedPost> findByIdAndUser_Id(Long id, Long userId);
}
