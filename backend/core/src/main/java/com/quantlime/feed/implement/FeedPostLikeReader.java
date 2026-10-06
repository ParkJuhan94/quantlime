package com.quantlime.feed.implement;

import com.quantlime.feed.repository.FeedPostLikeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 피드 좋아요 조회를 감싸는 구현 레이어(Implementation). 메서드 이름은 Repository와 같다. */
@Component
@RequiredArgsConstructor
public class FeedPostLikeReader {

    private final FeedPostLikeRepository feedPostLikeRepository;

    public boolean existsByUser_IdAndFeedPost_Id(Long userId, Long feedPostId) {
        return feedPostLikeRepository.existsByUser_IdAndFeedPost_Id(userId, feedPostId);
    }

    public List<Object[]> countByPostIds(List<Long> postIds) {
        return feedPostLikeRepository.countByPostIds(postIds);
    }

    public List<Long> findLikedPostIds(Long userId, List<Long> postIds) {
        return feedPostLikeRepository.findLikedPostIds(userId, postIds);
    }
}
