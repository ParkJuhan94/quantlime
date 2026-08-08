package com.quantlime.videofeed.controller;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.videofeed.dto.CollectResult;
import com.quantlime.videofeed.dto.SummarizeResult;
import com.quantlime.videofeed.dto.TranscribeResult;
import com.quantlime.videofeed.dto.mapper.VideoFeedMapper;
import com.quantlime.videofeed.dto.response.ChannelResponse;
import com.quantlime.videofeed.exception.VideoFeedErrorCode;
import com.quantlime.videofeed.service.ChannelQueryService;
import com.quantlime.videofeed.service.ChannelVelocityInitializationService;
import com.quantlime.videofeed.service.FeedCollectionFacade;
import com.quantlime.videofeed.service.SummaryCollectionFacade;
import com.quantlime.videofeed.service.TranscriptCollectionFacade;
import com.quantlime.videofeed.service.VideoRetentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ROLE_ADMIN만 호출 가능(SecurityConfig의 /api/admin/** 매처 참고).
 * 정규 실행 경로는 FeedCollectionScheduler(하루 3회)이고, 이 엔드포인트는
 * 그 사이에 수동으로 즉시 실행하고 싶을 때 쓴다.
 */
@Tag(name = "피드 수집 관리자 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/feed")
public class FeedCollectionAdminController {

    private final FeedCollectionFacade feedCollectionFacade;
    private final ChannelVelocityInitializationService channelVelocityInitializationService;
    private final TranscriptCollectionFacade transcriptCollectionFacade;
    private final SummaryCollectionFacade summaryCollectionFacade;
    private final VideoRetentionService videoRetentionService;
    private final ChannelQueryService channelQueryService;

    @GetMapping("/channels")
    @Operation(summary = "채널 목록 조회",
        description = "시딩된 채널 전체(비활성 포함)를 우선순위 오름차순으로 조회한다. "
            + "channelId(velocity 초기화 트리거용 경로변수)를 DB 직접 조회 없이 확인하기 위한 용도")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<ChannelResponse>> channels() {
        return ResponseEntity.ok(channelQueryService.findAllOrderByPriority().stream()
            .map(VideoFeedMapper::toChannelResponse)
            .toList());
    }

    @PostMapping("/collect")
    @Operation(summary = "전체 채널 영상 수집 수동 트리거",
        description = "채널별 수집→적재→필터링을 즉시 실행한다. 정규 스케줄러가 이미 실행 중이면 거절된다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<CollectResult>> collect() {
        return ResponseEntity.ok(feedCollectionFacade.runAllExclusively()
            .orElseThrow(() -> new ValidationException(VideoFeedErrorCode.FEED_JOB_IN_PROGRESS)));
    }

    @PostMapping("/transcribe")
    @Operation(summary = "자막 수집 수동 트리거",
        description = "SELECTED(+ 재시도 상한 이내 FAILED) 영상 배치의 자막을 즉시 수집한다. "
            + "정규 스케줄러가 이미 실행 중이면 거절된다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<TranscribeResult>> transcribe() {
        return ResponseEntity.ok(transcriptCollectionFacade.runBatchExclusively()
            .orElseThrow(() -> new ValidationException(VideoFeedErrorCode.FEED_JOB_IN_PROGRESS)));
    }

    @PostMapping("/summarize")
    @Operation(summary = "AI 요약 생성 수동 트리거",
        description = "TRANSCRIBED(+ 재시도 상한 이내 FAILED) 영상 배치의 AI 요약을 즉시 생성한다. "
            + "정규 스케줄러가 이미 실행 중이면 거절된다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<SummarizeResult>> summarize() {
        return ResponseEntity.ok(summaryCollectionFacade.runBatchExclusively()
            .orElseThrow(() -> new ValidationException(VideoFeedErrorCode.FEED_JOB_IN_PROGRESS)));
    }

    @PostMapping("/channels/{channelId}/velocity/initialize")
    @Operation(summary = "채널 중앙값 업로드 속도 초기 산정", description = "최근 30개 업로드의 views/hours 중앙값을 계산해 저장한다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<BigDecimal> initializeVelocity(@PathVariable Long channelId) {
        return ResponseEntity.ok(channelVelocityInitializationService.initializeMedianVelocity(channelId));
    }

    @PostMapping("/retention/cleanup")
    @Operation(summary = "보존 기간 초과 영상 데이터 수동 정리",
        description = "발행일이 보존 기간(14일)을 초과한 영상+자막+요약+태깅종목을 즉시 삭제한다. "
            + "정규 스케줄러(매일 새벽 3시)가 이미 실행 중이면 거절된다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Integer> cleanupRetention() {
        return ResponseEntity.ok(videoRetentionService.runExclusively()
            .orElseThrow(() -> new ValidationException(VideoFeedErrorCode.RETENTION_JOB_IN_PROGRESS)));
    }
}
