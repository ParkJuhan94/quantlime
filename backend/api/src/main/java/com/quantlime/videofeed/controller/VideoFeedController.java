package com.quantlime.videofeed.controller;

import com.quantlime.common.dto.PageResponse;
import com.quantlime.videofeed.dto.response.VideoFeedDetailResponse;
import com.quantlime.videofeed.dto.response.VideoFeedItemResponse;
import com.quantlime.videofeed.service.VideoFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "투자 콘텐츠 요약 피드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/video-feed")
public class VideoFeedController {

    private final VideoFeedService videoFeedService;

    @GetMapping("/videos")
    @Operation(
        summary = "요약된 영상 피드 목록 조회",
        description = "AI 요약까지 끝난 영상을 최신순으로 조회한다(로그인 불필요). "
            + "tickerCode를 지정하면 해당 종목이 태깅된 영상만, date(yyyy-MM-dd)를 "
            + "지정하면 해당 날짜에 게시된 영상만 조회한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<PageResponse<VideoFeedItemResponse>> getVideos(
        @RequestParam(required = false) String tickerCode,
        @RequestParam(required = false) LocalDate date,
        Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(videoFeedService.getVideos(tickerCode, date, pageable)));
    }

    @GetMapping("/videos/{videoId}")
    @Operation(summary = "영상 요약 상세 조회", description = "로그인 불필요. 요약 전 영상이면 404")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<VideoFeedDetailResponse> getVideoDetail(@PathVariable Long videoId) {
        return ResponseEntity.ok(videoFeedService.getVideoDetail(videoId));
    }
}
