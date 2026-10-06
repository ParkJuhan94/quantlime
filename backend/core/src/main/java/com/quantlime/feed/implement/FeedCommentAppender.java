package com.quantlime.feed.implement;

import com.quantlime.feed.domain.FeedComment;
import com.quantlime.feed.repository.FeedCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 피드 댓글 저장·삭제를 감싸는 구현 레이어. 트랜잭션 경계는 호출하는 서비스가 소유한다(파생 delete는 Repository의 @Transactional도 그대로 유효). */
@Component
@RequiredArgsConstructor
public class FeedCommentAppender {

    private final FeedCommentRepository feedCommentRepository;

    public void deleteByFeedPost_Id(Long feedPostId) {
        feedCommentRepository.deleteByFeedPost_Id(feedPostId);
    }

    public FeedComment save(FeedComment comment) {
        return feedCommentRepository.save(comment);
    }

    public void delete(FeedComment comment) {
        feedCommentRepository.delete(comment);
    }
}
