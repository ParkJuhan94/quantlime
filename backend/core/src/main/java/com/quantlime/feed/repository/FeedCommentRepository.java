package com.quantlime.feed.repository;

import com.quantlime.feed.domain.FeedComment;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FeedCommentRepository extends JpaRepository<FeedComment, Long> {

    @Query("select c from FeedComment c join fetch c.user where c.feedPost.id = :postId order by c.id asc")
    Slice<FeedComment> findByFeedPostIdOrderByIdAsc(@Param("postId") Long postId, Pageable pageable);

    @Query("select c.feedPost.id, count(c) from FeedComment c where c.feedPost.id in :postIds group by c.feedPost.id")
    List<Object[]> countByPostIds(@Param("postIds") List<Long> postIds);

    // 게시글 삭제 시 같이 정리한다(FK가 NO_CONSTRAINT라 DB 캐스케이드가
    // 없어 직접 지워야 함, FeedService.deletePost 참고).
    @Transactional
    void deleteByFeedPost_Id(Long feedPostId);
}
