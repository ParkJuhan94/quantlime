package com.quantlime.feed.repository;

import com.quantlime.feed.domain.FeedComment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FeedCommentRepository extends JpaRepository<FeedComment, Long> {

    @Query("select c from FeedComment c join fetch c.user where c.feedPost.id = :postId and c.hidden = false order by c.id asc")
    Slice<FeedComment> findByFeedPostIdOrderByIdAsc(@Param("postId") Long postId, Pageable pageable);

    @Query("select c.feedPost.id, count(c) from FeedComment c where c.feedPost.id in :postIds and c.hidden = false group by c.feedPost.id")
    List<Object[]> countByPostIds(@Param("postIds") List<Long> postIds);

    @Query("select c from FeedComment c join fetch c.user where c.hidden = true order by c.id desc")
    Slice<FeedComment> findHiddenOrderByIdDesc(Pageable pageable);

    Optional<FeedComment> findByIdAndHiddenFalse(Long id);

    // 관리자가 글을 삭제할 때 그 글의 댓글 id들을 모아 신고 기록도 함께 정리하기 위함.
    @Query("select c.id from FeedComment c where c.feedPost.id = :postId")
    List<Long> findIdsByFeedPostId(@Param("postId") Long postId);

    // 게시글 삭제 시 같이 정리한다(FK가 NO_CONSTRAINT라 DB 캐스케이드가
    // 없어 직접 지워야 함, FeedService.deletePost 참고).
    @Transactional
    void deleteByFeedPost_Id(Long feedPostId);
}
