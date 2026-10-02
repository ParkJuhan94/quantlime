package com.quantlime.feed.controller;

import com.quantlime.common.dto.PageResponse;
import com.quantlime.feed.dto.response.AdminFeedCommentResponse;
import com.quantlime.feed.dto.response.AdminFeedPostResponse;
import com.quantlime.feed.service.FeedModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ROLE_ADMIN만 호출 가능(SecurityConfig의 /api/admin/** 매처 참고). */
@Tag(name = "피드 모더레이션 관리자 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/feed-moderation")
public class FeedModerationAdminController {

    private final FeedModerationService feedModerationService;

    @GetMapping("/posts/hidden")
    @Operation(summary = "숨김 처리된 글 목록", description = "신고 누적 등으로 숨겨진 글을 최신순으로, 누적 신고 수와 함께 조회한다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<PageResponse<AdminFeedPostResponse>> hiddenPosts(Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(feedModerationService.getHiddenPosts(pageable)));
    }

    @GetMapping("/comments/hidden")
    @Operation(summary = "숨김 처리된 댓글 목록", description = "신고 누적 등으로 숨겨진 댓글을 최신순으로, 누적 신고 수와 함께 조회한다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<PageResponse<AdminFeedCommentResponse>> hiddenComments(Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(feedModerationService.getHiddenComments(pageable)));
    }

    @PostMapping("/posts/{postId}/restore")
    @Operation(summary = "숨김 글 복구", description = "다시 목록에 노출하고 누적 신고를 초기화한다")
    public ResponseEntity<Void> restorePost(@PathVariable Long postId) {
        feedModerationService.restorePost(postId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/comments/{commentId}/restore")
    @Operation(summary = "숨김 댓글 복구", description = "다시 노출하고 누적 신고를 초기화한다")
    public ResponseEntity<Void> restoreComment(@PathVariable Long commentId) {
        feedModerationService.restoreComment(commentId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "글 영구 삭제", description = "글과 그 댓글·좋아요·신고 기록을 함께 삭제한다(되돌릴 수 없음)")
    public ResponseEntity<Void> deletePost(@PathVariable Long postId) {
        feedModerationService.deletePost(postId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "댓글 영구 삭제", description = "댓글과 신고 기록을 삭제한다(되돌릴 수 없음)")
    public ResponseEntity<Void> deleteComment(@PathVariable Long commentId) {
        feedModerationService.deleteComment(commentId);
        return ResponseEntity.noContent().build();
    }
}
