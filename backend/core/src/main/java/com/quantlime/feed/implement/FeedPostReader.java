package com.quantlime.feed.implement;

import com.quantlime.feed.domain.FeedCategory;
import com.quantlime.feed.domain.FeedPost;
import com.quantlime.feed.repository.FeedPostRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

/** 피드 게시글 조회를 감싸는 구현 레이어(Implementation). 메서드 이름은 Repository와 같다. */
@Component
@RequiredArgsConstructor
public class FeedPostReader {

    private final FeedPostRepository feedPostRepository;

    public Slice<FeedPost> findAllOrderByIdDesc(Pageable pageable) {
        return feedPostRepository.findAllOrderByIdDesc(pageable);
    }

    public Optional<FeedPost> findByIdAndUser_Id(Long id, Long userId) {
        return feedPostRepository.findByIdAndUser_Id(id, userId);
    }

    public Optional<FeedPost> findByIdAndHiddenFalse(Long id) {
        return feedPostRepository.findByIdAndHiddenFalse(id);
    }

    public Slice<FeedPost> findHiddenOrderByIdDesc(Pageable pageable) {
        return feedPostRepository.findHiddenOrderByIdDesc(pageable);
    }

    public Optional<FeedPost> findById(Long id) {
        return feedPostRepository.findById(id);
    }

    public Slice<FeedPost> findByCategoryOrderByIdDesc(FeedCategory category, Pageable pageable) {
        return feedPostRepository.findByCategoryOrderByIdDesc(category, pageable);
    }
}
