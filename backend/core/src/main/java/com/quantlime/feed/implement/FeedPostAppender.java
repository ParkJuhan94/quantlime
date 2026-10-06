package com.quantlime.feed.implement;

import com.quantlime.feed.domain.FeedPost;
import com.quantlime.feed.repository.FeedPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 피드 게시글 저장·삭제를 감싸는 구현 레이어. 트랜잭션 경계는 호출하는 서비스가 소유한다(파생 delete는 Repository의 @Transactional도 그대로 유효). */
@Component
@RequiredArgsConstructor
public class FeedPostAppender {

    private final FeedPostRepository feedPostRepository;

    public FeedPost save(FeedPost post) {
        return feedPostRepository.save(post);
    }

    public void delete(FeedPost post) {
        feedPostRepository.delete(post);
    }
}
