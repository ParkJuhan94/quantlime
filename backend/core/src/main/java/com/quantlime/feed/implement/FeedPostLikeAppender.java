package com.quantlime.feed.implement;

import com.quantlime.feed.domain.FeedPostLike;
import com.quantlime.feed.repository.FeedPostLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 피드 좋아요 저장·삭제를 감싸는 구현 레이어. 트랜잭션 경계는 호출하는 서비스가 소유한다(파생 delete는 Repository의 @Transactional도 그대로 유효). */
@Component
@RequiredArgsConstructor
public class FeedPostLikeAppender {

    private final FeedPostLikeRepository feedPostLikeRepository;

    public void deleteByUser_IdAndFeedPost_Id(Long userId, Long feedPostId) {
        feedPostLikeRepository.deleteByUser_IdAndFeedPost_Id(userId, feedPostId);
    }

    public void deleteByFeedPost_Id(Long feedPostId) {
        feedPostLikeRepository.deleteByFeedPost_Id(feedPostId);
    }

    public FeedPostLike save(FeedPostLike like) {
        return feedPostLikeRepository.save(like);
    }
}
