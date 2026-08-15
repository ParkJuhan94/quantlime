package com.quantlime.telegramfeed.controller;

import com.quantlime.common.dto.PageResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedChannelResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedDetailResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedPostResponse;
import com.quantlime.telegramfeed.service.TelegramFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// VideoFeedController와 구조적으로 동일 - 로그인 불필요(공개 조회).
@Tag(name = "텔레그램 요약 피드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/telegram-feed")
public class TelegramFeedController {

    private final TelegramFeedService telegramFeedService;

    @GetMapping("/posts")
    @Operation(
        summary = "요약된 텔레그램 글 목록 조회",
        description = "AI 요약까지 끝난 텔레그램 글을 최신순으로 조회한다(로그인 불필요). "
            + "tickerCode를 지정하면 해당 종목이 태깅된 글만, channelId를 지정하면 "
            + "해당 채널 글만, date(yyyy-MM-dd)를 지정하면 해당 날짜에 게시된 글만 조회한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<PageResponse<TelegramFeedPostResponse>> getPosts(
        @RequestParam(required = false) String tickerCode,
        @RequestParam(required = false) Long channelId,
        @RequestParam(required = false) LocalDate date,
        Pageable pageable) {
        return ResponseEntity.ok(
            PageResponse.of(telegramFeedService.getPosts(tickerCode, channelId, date, pageable)));
    }

    @GetMapping("/channels")
    @Operation(
        summary = "텔레그램 피드 채널 필터 목록 조회",
        description = "채널 필터 UI 구성을 위한 활성 채널 목록(우선순위순, 로그인 불필요)"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<TelegramFeedChannelResponse>> getChannels() {
        return ResponseEntity.ok(telegramFeedService.getChannels());
    }

    @GetMapping("/posts/{telegramPostId}")
    @Operation(summary = "텔레그램 글 요약 상세 조회", description = "로그인 불필요. 요약 전 글이면 404")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<TelegramFeedDetailResponse> getPostDetail(@PathVariable Long telegramPostId) {
        return ResponseEntity.ok(telegramFeedService.getPostDetail(telegramPostId));
    }
}
