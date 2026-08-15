package com.quantlime.telegramfeed.controller;

import com.quantlime.common.dto.PageResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedChannelResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedDigestDetailResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedDigestResponse;
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
// 2026-08-15부로 글 단위(/posts)가 아니라 채널×날짜 다이제스트 단위
// (/digests)로 조회한다.
@Tag(name = "텔레그램 요약 피드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/telegram-feed")
public class TelegramFeedController {

    private final TelegramFeedService telegramFeedService;

    @GetMapping("/digests")
    @Operation(
        summary = "텔레그램 다이제스트 목록 조회",
        description = "채널×날짜 단위로 생성된 AI 다이제스트를 최신순으로 조회한다(로그인 불필요). "
            + "tickerCode를 지정하면 해당 종목이 태깅된 다이제스트만, channelId를 지정하면 "
            + "해당 채널 다이제스트만, date(yyyy-MM-dd)를 지정하면 해당 날짜 다이제스트만 조회한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<PageResponse<TelegramFeedDigestResponse>> getDigests(
        @RequestParam(required = false) String tickerCode,
        @RequestParam(required = false) Long channelId,
        @RequestParam(required = false) LocalDate date,
        Pageable pageable) {
        return ResponseEntity.ok(
            PageResponse.of(telegramFeedService.getDigests(tickerCode, channelId, date, pageable)));
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

    @GetMapping("/digests/{telegramDigestId}")
    @Operation(summary = "텔레그램 다이제스트 상세 조회",
        description = "요약 전문/핵심포인트/고지문/원문 링크 목록을 모두 반환한다(로그인 불필요)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<TelegramFeedDigestDetailResponse> getDigestDetail(@PathVariable Long telegramDigestId) {
        return ResponseEntity.ok(telegramFeedService.getDigestDetail(telegramDigestId));
    }
}
