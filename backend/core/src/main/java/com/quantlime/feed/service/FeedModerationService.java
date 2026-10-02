package com.quantlime.feed.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.feed.domain.FeedComment;
import com.quantlime.feed.domain.FeedPost;
import com.quantlime.feed.domain.FeedReport;
import com.quantlime.feed.domain.FeedReportReason;
import com.quantlime.feed.domain.FeedReportTarget;
import com.quantlime.feed.dto.response.AdminFeedCommentResponse;
import com.quantlime.feed.dto.response.AdminFeedPostResponse;
import com.quantlime.feed.exception.FeedErrorCode;
import com.quantlime.feed.repository.FeedCommentRepository;
import com.quantlime.feed.repository.FeedPostLikeRepository;
import com.quantlime.feed.repository.FeedPostRepository;
import com.quantlime.feed.repository.FeedReportRepository;
import com.quantlime.user.domain.User;
import com.quantlime.user.exception.UserErrorCode;
import com.quantlime.user.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피드 신고/자동 숨김/관리자 검토(2026-10-01 결정 - 수익인증 탭 도배·광고를 운영 인력 없이 걸러내기 위함).
 *
 * <p>서로 다른 신고자 {@value #AUTO_HIDE_THRESHOLD}명이 신고하면 대상이 자동 숨김되어 목록에서 빠지고,
 * 관리자가 복구(신고 초기화)하거나 영구 삭제한다. 같은 사용자의 중복 신고는 조용히 무시한다(멱등).
 * 임계치가 작을수록 악의적 다중 신고에 취약하므로(신고 계정을 여러 개 만들면 정상 글도 숨길 수 있다)
 * 숨김은 삭제가 아니라 관리자가 되살릴 수 있는 상태로만 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedModerationService {

    static final int AUTO_HIDE_THRESHOLD = 3;

    private final FeedPostRepository feedPostRepository;
    private final FeedCommentRepository feedCommentRepository;
    private final FeedPostLikeRepository feedPostLikeRepository;
    private final FeedReportRepository feedReportRepository;
    private final UserRepository userRepository;

    @Transactional
    public void reportPost(Long userId, Long postId, FeedReportReason reason) {
        FeedPost post = feedPostRepository.findByIdAndHiddenFalse(postId)
            .orElseThrow(() -> new NotFoundException(FeedErrorCode.POST_NOT_FOUND));
        validateNotOwn(userId, post.getUser().getId());
        if (addReport(userId, FeedReportTarget.POST, postId, reason)
            && isOverThreshold(FeedReportTarget.POST, postId)) {
            post.hide();
            log.info("피드 글 신고 누적으로 자동 숨김: postId={}", postId);
        }
    }

    @Transactional
    public void reportComment(Long userId, Long commentId, FeedReportReason reason) {
        FeedComment comment = feedCommentRepository.findByIdAndHiddenFalse(commentId)
            .orElseThrow(() -> new NotFoundException(FeedErrorCode.COMMENT_NOT_FOUND));
        validateNotOwn(userId, comment.getUser().getId());
        if (addReport(userId, FeedReportTarget.COMMENT, commentId, reason)
            && isOverThreshold(FeedReportTarget.COMMENT, commentId)) {
            comment.hide();
            log.info("피드 댓글 신고 누적으로 자동 숨김: commentId={}", commentId);
        }
    }

    @Transactional(readOnly = true)
    public Slice<AdminFeedPostResponse> getHiddenPosts(Pageable pageable) {
        Slice<FeedPost> posts = feedPostRepository.findHiddenOrderByIdDesc(pageable);
        Map<Long, Long> counts = reportCounts(FeedReportTarget.POST, posts.getContent().stream().map(FeedPost::getId).toList());
        return posts.map(post -> new AdminFeedPostResponse(
            post.getId(), post.getUser().getNickname(), post.getCategory().getLabel(), post.getTitle(),
            post.getImageUrl(), counts.getOrDefault(post.getId(), 0L), post.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public Slice<AdminFeedCommentResponse> getHiddenComments(Pageable pageable) {
        Slice<FeedComment> comments = feedCommentRepository.findHiddenOrderByIdDesc(pageable);
        Map<Long, Long> counts =
            reportCounts(FeedReportTarget.COMMENT, comments.getContent().stream().map(FeedComment::getId).toList());
        return comments.map(comment -> new AdminFeedCommentResponse(
            comment.getId(), comment.getFeedPost().getId(), comment.getUser().getNickname(), comment.getContent(),
            counts.getOrDefault(comment.getId(), 0L), comment.getCreatedAt()));
    }

    @Transactional
    public void restorePost(Long postId) {
        FeedPost post = feedPostRepository.findById(postId)
            .orElseThrow(() -> new NotFoundException(FeedErrorCode.POST_NOT_FOUND));
        post.restore();
        feedReportRepository.deleteByTargetTypeAndTargetId(FeedReportTarget.POST, postId);
    }

    @Transactional
    public void restoreComment(Long commentId) {
        FeedComment comment = feedCommentRepository.findById(commentId)
            .orElseThrow(() -> new NotFoundException(FeedErrorCode.COMMENT_NOT_FOUND));
        comment.restore();
        feedReportRepository.deleteByTargetTypeAndTargetId(FeedReportTarget.COMMENT, commentId);
    }

    // 좋아요/댓글/신고 모두 FK가 NO_CONSTRAINT라 DB 캐스케이드가 없어 직접 정리한다(FeedService.deletePost와 동일).
    @Transactional
    public void deletePost(Long postId) {
        FeedPost post = feedPostRepository.findById(postId)
            .orElseThrow(() -> new NotFoundException(FeedErrorCode.POST_NOT_FOUND));
        List<Long> commentIds = feedCommentRepository.findIdsByFeedPostId(postId);
        feedReportRepository.deleteByTargetTypeAndTargetIdIn(FeedReportTarget.COMMENT, commentIds);
        feedReportRepository.deleteByTargetTypeAndTargetId(FeedReportTarget.POST, postId);
        feedCommentRepository.deleteByFeedPost_Id(postId);
        feedPostLikeRepository.deleteByFeedPost_Id(postId);
        feedPostRepository.delete(post);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        FeedComment comment = feedCommentRepository.findById(commentId)
            .orElseThrow(() -> new NotFoundException(FeedErrorCode.COMMENT_NOT_FOUND));
        feedReportRepository.deleteByTargetTypeAndTargetId(FeedReportTarget.COMMENT, commentId);
        feedCommentRepository.delete(comment);
    }

    private void validateNotOwn(Long reporterId, Long ownerId) {
        if (reporterId.equals(ownerId)) {
            throw new ValidationException(FeedErrorCode.CANNOT_REPORT_OWN_CONTENT);
        }
    }

    /** @return 이번 호출로 새 신고가 쌓였으면 true, 이미 신고한 사용자의 중복이면 false */
    private boolean addReport(Long userId, FeedReportTarget target, Long targetId, FeedReportReason reason) {
        if (feedReportRepository.existsByReporter_IdAndTargetTypeAndTargetId(userId, target, targetId)) {
            return false;
        }
        User reporter = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(UserErrorCode.NOT_FOUND_USER));
        feedReportRepository.save(FeedReport.of(reporter, target, targetId, reason));
        return true;
    }

    private boolean isOverThreshold(FeedReportTarget target, Long targetId) {
        return feedReportRepository.countByTargetTypeAndTargetId(target, targetId) >= AUTO_HIDE_THRESHOLD;
    }

    private Map<Long, Long> reportCounts(FeedReportTarget target, List<Long> ids) {
        Map<Long, Long> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Object[] row : feedReportRepository.countByTargetIds(target, ids)) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }
}
