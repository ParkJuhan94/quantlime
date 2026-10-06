package com.quantlime.feed.implement;

import com.quantlime.feed.domain.FeedComment;
import com.quantlime.feed.repository.FeedCommentRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

/** 피드 댓글 조회를 감싸는 구현 레이어(Implementation). 메서드 이름은 Repository와 같다. */
@Component
@RequiredArgsConstructor
public class FeedCommentReader {

    private final FeedCommentRepository feedCommentRepository;

    public Slice<FeedComment> findHiddenOrderByIdDesc(Pageable pageable) {
        return feedCommentRepository.findHiddenOrderByIdDesc(pageable);
    }

    public Optional<FeedComment> findByIdAndHiddenFalse(Long id) {
        return feedCommentRepository.findByIdAndHiddenFalse(id);
    }

    public Optional<FeedComment> findById(Long id) {
        return feedCommentRepository.findById(id);
    }

    public Slice<FeedComment> findByFeedPostIdOrderByIdAsc(Long postId, Pageable pageable) {
        return feedCommentRepository.findByFeedPostIdOrderByIdAsc(postId, pageable);
    }

    public List<Object[]> countByPostIds(List<Long> postIds) {
        return feedCommentRepository.countByPostIds(postIds);
    }

    public List<Long> findIdsByFeedPostId(Long postId) {
        return feedCommentRepository.findIdsByFeedPostId(postId);
    }
}
