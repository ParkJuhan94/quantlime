package com.quantlab.feed.service;

import com.quantlab.common.exception.NotFoundException;
import com.quantlab.feed.domain.FeedCategory;
import com.quantlab.feed.domain.FeedComment;
import com.quantlab.feed.domain.FeedPost;
import com.quantlab.feed.domain.FeedPostLike;
import com.quantlab.feed.dto.mapper.FeedMapper;
import com.quantlab.feed.dto.request.CreateFeedCommentRequest;
import com.quantlab.feed.dto.request.CreateFeedPostRequest;
import com.quantlab.feed.dto.request.UpdateFeedPostRequest;
import com.quantlab.feed.dto.response.FeedCommentResponse;
import com.quantlab.feed.dto.response.FeedPostResponse;
import com.quantlab.feed.exception.FeedErrorCode;
import com.quantlab.feed.repository.FeedCommentRepository;
import com.quantlab.feed.repository.FeedPostLikeRepository;
import com.quantlab.feed.repository.FeedPostRepository;
import com.quantlab.user.domain.User;
import com.quantlab.user.exception.UserErrorCode;
import com.quantlab.user.repository.UserRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedService {

    private final FeedPostRepository feedPostRepository;
    private final FeedPostLikeRepository feedPostLikeRepository;
    private final FeedCommentRepository feedCommentRepository;
    private final UserRepository userRepository;

    @Transactional
    public FeedPostResponse createPost(Long userId, CreateFeedPostRequest request) {
        User user = findUser(userId);
        FeedCategory category = FeedCategory.of(request.category());
        FeedPost post = feedPostRepository.save(FeedPost.of(user, category, request.title(), request.imageUrl()));
        return FeedMapper.toFeedPostResponse(post, 0, 0, false, true);
    }

    @Transactional(readOnly = true)
    public Slice<FeedPostResponse> getPosts(String categoryLabel, Long userId, Pageable pageable) {
        Slice<FeedPost> posts = categoryLabel == null
            ? feedPostRepository.findAllOrderByIdDesc(pageable)
            : feedPostRepository.findByCategoryOrderByIdDesc(FeedCategory.of(categoryLabel), pageable);

        List<Long> postIds = posts.getContent().stream().map(FeedPost::getId).toList();
        Map<Long, Long> likeCounts = toCountMap(feedPostLikeRepository.countByPostIds(postIds));
        Map<Long, Long> commentCounts = toCountMap(feedCommentRepository.countByPostIds(postIds));
        Set<Long> likedPostIds = userId != null
            ? new HashSet<>(feedPostLikeRepository.findLikedPostIds(userId, postIds))
            : Set.of();

        return posts.map(post -> FeedMapper.toFeedPostResponse(
            post,
            likeCounts.getOrDefault(post.getId(), 0L),
            commentCounts.getOrDefault(post.getId(), 0L),
            likedPostIds.contains(post.getId()),
            userId != null && userId.equals(post.getUser().getId())));
    }

    // 소유권 검증은 findOwnedPost(다른 사용자 글이면 404)에 위임한다 -
    // WatchlistGroupService.getOwnedGroup과 동일한 패턴(§ FeedPostRepository 참고).
    @Transactional
    public FeedPostResponse updatePost(Long userId, Long postId, UpdateFeedPostRequest request) {
        FeedPost post = findOwnedPost(userId, postId);
        FeedCategory category = FeedCategory.of(request.category());
        post.update(category, request.title(), request.imageUrl());

        long likeCount = toCountMap(feedPostLikeRepository.countByPostIds(List.of(postId))).getOrDefault(postId, 0L);
        long commentCount = toCountMap(feedCommentRepository.countByPostIds(List.of(postId))).getOrDefault(postId, 0L);
        boolean likedByMe = feedPostLikeRepository.existsByUser_IdAndFeedPost_Id(userId, postId);
        return FeedMapper.toFeedPostResponse(post, likeCount, commentCount, likedByMe, true);
    }

    // 좋아요/댓글 테이블은 feed_post_id를 NO_CONSTRAINT FK로만 참조해(§9.2
    // 관례) DB 캐스케이드가 없다 - 게시글 삭제 시 직접 먼저 정리해야
    // 고아 행이 안 남는다.
    @Transactional
    public void deletePost(Long userId, Long postId) {
        FeedPost post = findOwnedPost(userId, postId);
        feedCommentRepository.deleteByFeedPost_Id(postId);
        feedPostLikeRepository.deleteByFeedPost_Id(postId);
        feedPostRepository.delete(post);
    }

    // 중복 클릭(더블 클릭, 네트워크 재시도)으로 좋아요가 두 번 눌려도
    // 유니크 제약(FeedPostLike)에 걸려 예외가 나지 않도록 존재 여부를
    // 먼저 확인하고 조용히 무시한다 - 관심종목 등록과 다르게 "이미
    // 좋아요한 상태"는 사용자에게 에러로 보여줄 이유가 없는 멱등 동작.
    @Transactional
    public void likePost(Long userId, Long postId) {
        if (feedPostLikeRepository.existsByUser_IdAndFeedPost_Id(userId, postId)) {
            return;
        }
        User user = findUser(userId);
        FeedPost post = findPost(postId);
        feedPostLikeRepository.save(FeedPostLike.of(user, post));
    }

    @Transactional
    public void unlikePost(Long userId, Long postId) {
        feedPostLikeRepository.deleteByUser_IdAndFeedPost_Id(userId, postId);
    }

    @Transactional
    public FeedCommentResponse createComment(Long userId, Long postId, CreateFeedCommentRequest request) {
        User user = findUser(userId);
        FeedPost post = findPost(postId);
        FeedComment comment = feedCommentRepository.save(FeedComment.of(user, post, request.content()));
        return FeedMapper.toFeedCommentResponse(comment);
    }

    @Transactional(readOnly = true)
    public Slice<FeedCommentResponse> getComments(Long postId, Pageable pageable) {
        return feedCommentRepository.findByFeedPostIdOrderByIdAsc(postId, pageable)
            .map(FeedMapper::toFeedCommentResponse);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(UserErrorCode.NOT_FOUND_USER));
    }

    private FeedPost findPost(Long postId) {
        return feedPostRepository.findById(postId)
            .orElseThrow(() -> new NotFoundException(FeedErrorCode.POST_NOT_FOUND));
    }

    // 다른 사용자의 글이면 존재 자체를 노출하지 않고 404로 응답한다
    // (WatchlistGroupService.getOwnedGroup과 동일한 패턴).
    private FeedPost findOwnedPost(Long userId, Long postId) {
        return feedPostRepository.findByIdAndUser_Id(postId, userId)
            .orElseThrow(() -> new NotFoundException(FeedErrorCode.POST_NOT_FOUND));
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }
}
