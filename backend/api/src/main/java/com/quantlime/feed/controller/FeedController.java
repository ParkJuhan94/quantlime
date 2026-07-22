package com.quantlime.feed.controller;

import com.quantlime.auth.resolver.LoginUser;
import com.quantlime.auth.resolver.OptionalLoginUser;
import com.quantlime.common.dto.PageResponse;
import com.quantlime.feed.dto.request.CreateFeedCommentRequest;
import com.quantlime.feed.dto.request.CreateFeedPostRequest;
import com.quantlime.feed.dto.request.UpdateFeedPostRequest;
import com.quantlime.feed.dto.response.FeedCommentResponse;
import com.quantlime.feed.dto.response.FeedPostResponse;
import com.quantlime.feed.service.FeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "피드(커뮤니티) API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedService feedService;

    @PostMapping("/posts")
    @Operation(summary = "피드 글 작성", description = "제목과 주제(카테고리)로 피드 글을 작성한다(로그인 필요)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<FeedPostResponse> createPost(
        @LoginUser Long userId,
        @Valid @RequestBody CreateFeedPostRequest request) {
        return ResponseEntity.ok(feedService.createPost(userId, request));
    }

    @GetMapping("/posts")
    @Operation(
        summary = "피드 글 목록 조회",
        description = "주제(category)를 생략하면 전체 글을 최신순으로 조회한다(로그인 불필요, 로그인 상태면 likedByMe가 채워진다)"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<PageResponse<FeedPostResponse>> getPosts(
        @RequestParam(required = false) String category,
        @OptionalLoginUser Long userId,
        Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(feedService.getPosts(category, userId, pageable)));
    }

    @PatchMapping("/posts/{postId}")
    @Operation(summary = "피드 글 수정", description = "작성자 본인만 수정할 수 있다(다른 사용자의 글이면 404)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<FeedPostResponse> updatePost(
        @LoginUser Long userId,
        @PathVariable Long postId,
        @Valid @RequestBody UpdateFeedPostRequest request) {
        return ResponseEntity.ok(feedService.updatePost(userId, postId, request));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "피드 글 삭제", description = "작성자 본인만 삭제할 수 있다(다른 사용자의 글이면 404)")
    public ResponseEntity<Void> deletePost(@LoginUser Long userId, @PathVariable Long postId) {
        feedService.deletePost(userId, postId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{postId}/like")
    @Operation(summary = "피드 글 좋아요", description = "이미 좋아요한 상태면 조용히 무시한다(로그인 필요)")
    public ResponseEntity<Void> likePost(@LoginUser Long userId, @PathVariable Long postId) {
        feedService.likePost(userId, postId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/posts/{postId}/like")
    @Operation(summary = "피드 글 좋아요 취소", description = "로그인 필요")
    public ResponseEntity<Void> unlikePost(@LoginUser Long userId, @PathVariable Long postId) {
        feedService.unlikePost(userId, postId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/posts/{postId}/comments")
    @Operation(summary = "피드 댓글 작성", description = "로그인 필요")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<FeedCommentResponse> createComment(
        @LoginUser Long userId,
        @PathVariable Long postId,
        @Valid @RequestBody CreateFeedCommentRequest request) {
        return ResponseEntity.ok(feedService.createComment(userId, postId, request));
    }

    @GetMapping("/posts/{postId}/comments")
    @Operation(summary = "피드 댓글 목록 조회", description = "로그인 불필요, 오래된 순")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<PageResponse<FeedCommentResponse>> getComments(
        @PathVariable Long postId,
        Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(feedService.getComments(postId, pageable)));
    }
}
