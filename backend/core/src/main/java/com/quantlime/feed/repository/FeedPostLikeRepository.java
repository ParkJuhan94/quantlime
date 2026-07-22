package com.quantlime.feed.repository;

import com.quantlime.feed.domain.FeedPostLike;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FeedPostLikeRepository extends JpaRepository<FeedPostLike, Long> {

    boolean existsByUser_IdAndFeedPost_Id(Long userId, Long feedPostId);

    @Transactional
    void deleteByUser_IdAndFeedPost_Id(Long userId, Long feedPostId);

    // 게시글 삭제 시 같이 정리한다(FK가 NO_CONSTRAINT라 DB 캐스케이드가
    // 없어 직접 지워야 함, FeedService.deletePost 참고).
    @Transactional
    void deleteByFeedPost_Id(Long feedPostId);

    // 목록 화면에서 글 하나마다 좋아요 수를 따로 조회하면 N+1이 되니
    // 페이지에 담긴 글 id들을 한 번에 묶어 집계한다.
    @Query("select l.feedPost.id, count(l) from FeedPostLike l where l.feedPost.id in :postIds group by l.feedPost.id")
    List<Object[]> countByPostIds(@Param("postIds") List<Long> postIds);

    @Query("select l.feedPost.id from FeedPostLike l where l.user.id = :userId and l.feedPost.id in :postIds")
    List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
