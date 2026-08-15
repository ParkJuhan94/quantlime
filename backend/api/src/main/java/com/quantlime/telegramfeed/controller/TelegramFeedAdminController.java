package com.quantlime.telegramfeed.controller;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.telegramfeed.dto.TelegramCollectResult;
import com.quantlime.telegramfeed.dto.TelegramSummarizeResult;
import com.quantlime.telegramfeed.dto.mapper.TelegramFeedMapper;
import com.quantlime.telegramfeed.dto.response.TelegramChannelResponse;
import com.quantlime.telegramfeed.exception.TelegramFeedErrorCode;
import com.quantlime.telegramfeed.service.TelegramChannelQueryService;
import com.quantlime.telegramfeed.service.TelegramCollectionFacade;
import com.quantlime.telegramfeed.service.TelegramPostRetentionService;
import com.quantlime.telegramfeed.service.TelegramSummaryCollectionFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ROLE_ADMIN만 호출 가능(SecurityConfig의 /api/admin/** 매처 참고).
 * 정규 실행 경로는 TelegramCollectionScheduler(하루 3회)이고, 이 엔드포인트는
 * 그 사이에 수동으로 즉시 실행하고 싶을 때 쓴다. FeedCollectionAdminController
 * (유튜브)와 대응하되 /transcribe, /channels/{id}/velocity/initialize는
 * 없다(자막 단계·velocity 개념 자체가 없음).
 */
@Tag(name = "텔레그램 피드 수집 관리자 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/telegram-feed")
public class TelegramFeedAdminController {

    private final TelegramCollectionFacade telegramCollectionFacade;
    private final TelegramSummaryCollectionFacade telegramSummaryCollectionFacade;
    private final TelegramPostRetentionService telegramPostRetentionService;
    private final TelegramChannelQueryService telegramChannelQueryService;

    @GetMapping("/channels")
    @Operation(summary = "텔레그램 채널 목록 조회", description = "시딩된 텔레그램 채널 전체(비활성 포함)를 우선순위 오름차순으로 조회한다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<TelegramChannelResponse>> channels() {
        return ResponseEntity.ok(telegramChannelQueryService.findAllOrderByPriority().stream()
            .map(TelegramFeedMapper::toChannelResponse)
            .toList());
    }

    @PostMapping("/collect")
    @Operation(summary = "전체 텔레그램 채널 글 수집 수동 트리거",
        description = "채널별 수집→적재→필터링을 즉시 실행한다. 정규 스케줄러가 이미 실행 중이면 거절된다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<TelegramCollectResult>> collect() {
        return ResponseEntity.ok(telegramCollectionFacade.runAllExclusively()
            .orElseThrow(() -> new ValidationException(TelegramFeedErrorCode.TELEGRAM_JOB_IN_PROGRESS)));
    }

    @PostMapping("/summarize")
    @Operation(summary = "AI 요약 생성 수동 트리거",
        description = "SELECTED(+ 재시도 상한 이내 FAILED) 텔레그램 글 배치의 AI 요약을 즉시 생성한다. "
            + "정규 스케줄러가 이미 실행 중이면 거절된다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<TelegramSummarizeResult>> summarize() {
        return ResponseEntity.ok(telegramSummaryCollectionFacade.runBatchExclusively()
            .orElseThrow(() -> new ValidationException(TelegramFeedErrorCode.TELEGRAM_JOB_IN_PROGRESS)));
    }

    @PostMapping("/retention/cleanup")
    @Operation(summary = "보존 기간 초과 텔레그램 글 데이터 수동 정리",
        description = "발행일이 보존 기간(14일)을 초과한 글+요약+태깅종목을 즉시 삭제한다. "
            + "정규 스케줄러(매일 새벽 3시 10분)가 이미 실행 중이면 거절된다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Integer> cleanupRetention() {
        return ResponseEntity.ok(telegramPostRetentionService.runExclusively()
            .orElseThrow(() -> new ValidationException(TelegramFeedErrorCode.RETENTION_JOB_IN_PROGRESS)));
    }
}
